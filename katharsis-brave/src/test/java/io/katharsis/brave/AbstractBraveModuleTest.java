package io.katharsis.brave;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.Brave.Builder;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.twitter.zipkin.gen.Endpoint;

import io.katharsis.brave.mock.models.Project;
import io.katharsis.brave.mock.models.Task;
import io.katharsis.brave.mock.repository.ProjectRepository;
import io.katharsis.brave.mock.repository.TaskRepository;
import io.katharsis.client.KatharsisClient;
import io.katharsis.client.QuerySpecRelationshipRepositoryStub;
import io.katharsis.client.QuerySpecResourceRepositoryStub;
import io.katharsis.client.http.HttpAdapter;
import io.katharsis.client.http.okhttp.OkHttpAdapter;
import io.katharsis.queryspec.FilterOperator;
import io.katharsis.queryspec.FilterSpec;
import io.katharsis.queryspec.QuerySpec;
import io.katharsis.rs.KatharsisFeature;
import io.katharsis.rs.KatharsisProperties;
import zipkin.BinaryAnnotation;
import zipkin.Span;
import zipkin.reporter.Reporter;

public abstract class AbstractBraveModuleTest extends JerseyTest {

	protected KatharsisClient client;

	protected QuerySpecResourceRepositoryStub<Task, Long> taskRepo;

	private Reporter<Span> clientReporter;

	private Reporter<Span> serverReporter;

	private HttpAdapter httpAdapter;

	private boolean isOkHttp;

	private QuerySpecResourceRepositoryStub<Project, Serializable> projectRepo;

	public AbstractBraveModuleTest(HttpAdapter httpAdapter) {
		this.httpAdapter = httpAdapter;
		this.isOkHttp = httpAdapter instanceof OkHttpAdapter;
	}

	@Before
	@SuppressWarnings("unchecked")
	public void setup() {
		Endpoint localEndpoint = Endpoint.builder().serviceName("testClient").build();

		clientReporter = Mockito.mock(Reporter.class);

		Builder builder = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint));
		builder.reporter(clientReporter);
		Brave clientBrave = builder.build();

		client = new KatharsisClient(getBaseUri().toString());
		client.setHttpAdapter(httpAdapter);
		client.addModule(BraveModule.newClientModule(clientBrave));
		taskRepo = client.getQuerySpecRepository(Task.class);
		projectRepo = client.getQuerySpecRepository(Project.class);
		TaskRepository.clear();
		ProjectRepository.clear();
		httpAdapter.setReceiveTimeout(10000, TimeUnit.SECONDS);
	}

	@Test
	public void testCreate() {
		Task task = new Task();
		task.setId(13L);
		task.setName("myTask");
		taskRepo.create(task);

		// check client call and link span
		ArgumentCaptor<Span> clientSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(clientReporter, Mockito.times(isOkHttp ? 2 : 1)).report(clientSpanCaptor.capture());
		List<Span> clientSpans = clientSpanCaptor.getAllValues();
		Span callSpan = clientSpans.get(0);
		Assert.assertEquals("post", callSpan.name);
		Assert.assertTrue(callSpan.toString().contains("\"cs\""));
		Assert.assertTrue(callSpan.toString().contains("\"cr\""));
		if (isOkHttp) {
			Span linkSpan = clientSpans.get(1);
			Assert.assertEquals("post", linkSpan.name);
			Assert.assertTrue(linkSpan.toString().contains("\"lc\""));
		}

		// check server local span
		ArgumentCaptor<Span> serverSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(serverReporter, Mockito.times(1)).report(serverSpanCaptor.capture());
		List<Span> serverSpans = serverSpanCaptor.getAllValues();
		Span repositorySpan = serverSpans.get(0);
		Assert.assertEquals("post /tasks/13/", repositorySpan.name);
		Assert.assertTrue(repositorySpan.toString().contains("\"lc\""));

		assertBinaryAnnotation(repositorySpan, "lc", "katharsis");
		assertBinaryAnnotation(repositorySpan, "katharsis.query", "?");
	}

	@Test
	public void testError() {
		Task task = new Task();
		task.setId(13L);
		try {
			taskRepo.create(task);
		}
		catch (Exception e) {
			// ok
		}

		// check client call and link span
		ArgumentCaptor<Span> clientSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(clientReporter, Mockito.times(isOkHttp ? 2 : 1)).report(clientSpanCaptor.capture());
		List<Span> clientSpans = clientSpanCaptor.getAllValues();
		Span callSpan = clientSpans.get(0);
		Assert.assertEquals("post", callSpan.name);
		Assert.assertTrue(callSpan.toString().contains("\"cs\""));
		Assert.assertTrue(callSpan.toString().contains("\"cr\""));
		assertBinaryAnnotation(callSpan, "http.status_code", "500");
		if (isOkHttp) {
			Span linkSpan = clientSpans.get(1);
			Assert.assertEquals("post", linkSpan.name);
			Assert.assertTrue(linkSpan.toString().contains("\"lc\""));
		}

		// check server local span
		ArgumentCaptor<Span> serverSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(serverReporter, Mockito.times(1)).report(serverSpanCaptor.capture());
		List<Span> serverSpans = serverSpanCaptor.getAllValues();
		Span repositorySpan = serverSpans.get(0);
		Assert.assertEquals("post /tasks/13/", repositorySpan.name);
		Assert.assertTrue(repositorySpan.toString().contains("\"lc\""));

		assertBinaryAnnotation(repositorySpan, "lc", "katharsis");
		assertBinaryAnnotation(repositorySpan, "katharsis.query", "?");
		assertBinaryAnnotation(repositorySpan, "katharsis.status", "EXCEPTION");
	}

	@Test
	public void testFindAll() {
		Task task = new Task();
		task.setId(13L);
		task.setName("myTask");
		QuerySpec querySpec = new QuerySpec(Task.class);
		querySpec.addFilter(new FilterSpec(Arrays.asList("name"), FilterOperator.EQ, "doe"));
		taskRepo.findAll(querySpec);

		// check client call and link span
		ArgumentCaptor<Span> clientSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(clientReporter, Mockito.times(isOkHttp ? 2 : 1)).report(clientSpanCaptor.capture());
		List<Span> clientSpans = clientSpanCaptor.getAllValues();
		Span callSpan = clientSpans.get(0);
		Assert.assertEquals("get", callSpan.name);
		Assert.assertTrue(callSpan.toString().contains("\"cs\""));
		Assert.assertTrue(callSpan.toString().contains("\"cr\""));
		if (isOkHttp) {
			Span linkSpan = clientSpans.get(1);
			Assert.assertEquals("get", linkSpan.name);
			Assert.assertTrue(linkSpan.toString().contains("\"lc\""));
		}

		// check server local span
		ArgumentCaptor<Span> serverSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(serverReporter, Mockito.times(1)).report(serverSpanCaptor.capture());
		List<Span> serverSpans = serverSpanCaptor.getAllValues();
		Span repositorySpan = serverSpans.get(0);
		Assert.assertEquals("get /tasks/", repositorySpan.name);
		Assert.assertTrue(repositorySpan.toString().contains("\"lc\""));

		assertBinaryAnnotation(repositorySpan, "lc", "katharsis");
		assertBinaryAnnotation(repositorySpan, "katharsis.query", "?filter[tasks][name][EQ]=doe");
		assertBinaryAnnotation(repositorySpan, "katharsis.results", "0");
		assertBinaryAnnotation(repositorySpan, "katharsis.status", "OK");
	}

	@Test
	public void testFindTargets() {
		QuerySpecRelationshipRepositoryStub<Project, Serializable, Task, Serializable> relRepo = client
				.getQuerySpecRepository(Project.class, Task.class);
		relRepo.findManyTargets(123L, "tasks", new QuerySpec(Task.class));

		// check client call and link span
		ArgumentCaptor<Span> clientSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(clientReporter, Mockito.times(isOkHttp ? 2 : 1)).report(clientSpanCaptor.capture());
		List<Span> clientSpans = clientSpanCaptor.getAllValues();
		Span callSpan = clientSpans.get(0);
		Assert.assertEquals("get", callSpan.name);
		Assert.assertTrue(callSpan.toString().contains("\"cs\""));
		Assert.assertTrue(callSpan.toString().contains("\"cr\""));
		if (isOkHttp) {
			Span linkSpan = clientSpans.get(1);
			Assert.assertEquals("get", linkSpan.name);
			Assert.assertTrue(linkSpan.toString().contains("\"lc\""));
		}

		// check server local span
		ArgumentCaptor<Span> serverSpanCaptor = ArgumentCaptor.forClass(Span.class);
		Mockito.verify(serverReporter, Mockito.times(2)).report(serverSpanCaptor.capture());
		List<Span> serverSpans = serverSpanCaptor.getAllValues();

		Span repositorySpan0 = serverSpans.get(0);
		Assert.assertEquals("get /tasks/", repositorySpan0.name);
		Assert.assertTrue(repositorySpan0.toString().contains("\"lc\""));

		assertBinaryAnnotation(repositorySpan0, "lc", "katharsis");
		assertBinaryAnnotation(repositorySpan0, "katharsis.results", "0");
		assertBinaryAnnotation(repositorySpan0, "katharsis.status", "OK");

		Span repositorySpan1 = serverSpans.get(1);
		Assert.assertEquals("get /projects/123/tasks/", repositorySpan1.name);
		Assert.assertTrue(repositorySpan1.toString().contains("\"lc\""));

		assertBinaryAnnotation(repositorySpan1, "lc", "katharsis");
		assertBinaryAnnotation(repositorySpan1, "katharsis.query", "?");
		assertBinaryAnnotation(repositorySpan1, "katharsis.results", "0");
		assertBinaryAnnotation(repositorySpan1, "katharsis.status", "OK");
	}

	private void assertBinaryAnnotation(Span span, String name, String value) {
		for (BinaryAnnotation a : span.binaryAnnotations) {
			if (a.key.equals(name)) {
				if (value != null) {
					Assert.assertEquals(value, getValue(a));
				}
				return;
			}
		}
		Assert.fail(name + " not found");
	}

	public static Object getValue(BinaryAnnotation annotation) {
		try {
			return new String(annotation.value, "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	protected Application configure() {
		return new TestApplication();
	}

	@ApplicationPath("/")
	private class TestApplication extends ResourceConfig {

		@SuppressWarnings("unchecked")
		public TestApplication() {
			property(KatharsisProperties.RESOURCE_SEARCH_PACKAGE, getClass().getPackage().getName());
			property(KatharsisProperties.RESOURCE_DEFAULT_DOMAIN, "http://test.local");

			Endpoint localEndpoint = Endpoint.builder().serviceName("testServer").build();
			Builder builder = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint));
			serverReporter = Mockito.mock(Reporter.class);
			builder.reporter(serverReporter);
			Brave serverBrave = builder.build();

			KatharsisFeature feature = new KatharsisFeature();
			feature.addModule(BraveModule.newServerModule(serverBrave));
			register(feature);
		}
	}
}
