package io.katharsis.queryspec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.katharsis.resource.list.ResourceList;
import io.katharsis.resource.mock.models.Project;
import io.katharsis.resource.mock.models.Task;
import io.katharsis.response.paging.PagedMetaInformation;

public class InMemoryEvaluatorTest {

	private List<Task> tasks;

	@Before
	public void setup() {
		Project project1 = new Project();
		project1.setId(13L);
		Project project2 = new Project();
		project2.setId(14L);

		tasks = new ArrayList<>();
		for (long i = 0; i < 5; i++) {
			Task task = new Task();
			task.setId(i);
			task.setName("test" + i);
			task.setProjects(new ArrayList<Project>());
			tasks.add(task);

			if (i == 0) {
				task.getProjects().add(project1);
			}
			if (i < 2) {
				task.getProjects().add(project2);
			}
		}

	}

	@Test
	public void testAll() {
		QuerySpec spec = new QuerySpec(Task.class);
		Assert.assertEquals(5, spec.apply(tasks).size());
	}

	@Test
	public void setLimit() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.setLimit(2L);
		Assert.assertEquals(2, spec.apply(tasks).size());
	}

	@Test
	public void setOffset() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.setOffset(2L);
		Assert.assertEquals(3, spec.apply(tasks).size());
	}

	@Test
	public void setOffsetLimit() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.setOffset(2L);
		spec.setLimit(1L);
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(1, results.size());
		Assert.assertEquals(Long.valueOf(2L), results.get(0).getId());
	}

	@Test
	public void testSortAsc() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addSort(new SortSpec(Arrays.asList("name"), Direction.ASC));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(5, results.size());
		Assert.assertEquals("test0", results.get(0).getName());
	}

	@Test
	public void testMultiColumnSort() {
		tasks.clear();
		for (long i = 0; i < 5; i++) {
			Task task = new Task();
			task.setId(i);
			task.setName("test");
			tasks.add(task);
		}

		QuerySpec spec = new QuerySpec(Task.class);
		spec.addSort(new SortSpec(Arrays.asList("name"), Direction.ASC));
		spec.addSort(new SortSpec(Arrays.asList("id"), Direction.DESC));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(5, results.size());
		Assert.assertEquals(4L, results.get(0).getId().longValue());
		Assert.assertEquals(0L, results.get(4).getId().longValue());
	}

	@Test
	public void testSortDesc() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addSort(new SortSpec(Arrays.asList("name"), Direction.DESC));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(5, results.size());
		Assert.assertEquals("test4", results.get(0).getName());
	}

	@Test
	public void testFilterEquals() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.setLimit(10000L);
		spec.addFilter(new FilterSpec(Arrays.asList("name"), FilterOperator.EQ, "test1"));
		ResourceList<Task> results = spec.apply(tasks);
		Assert.assertEquals(1, results.size());
		PagedMetaInformation meta = results.getMeta(PagedMetaInformation.class);
		Assert.assertEquals(1L, meta.getTotalResourceCount().longValue());
		Assert.assertEquals("test1", results.get(0).getName());
	}

	@Test
	public void testFilterByMultiValuedAttribute1() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("projects", "id"), FilterOperator.EQ, 13L));
		ResourceList<Task> results = spec.apply(tasks);
		Assert.assertEquals(1, results.size());
	}

	@Test
	public void testFilterByMultiValuedAttribute2() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("projects", "id"), FilterOperator.EQ, 14L));
		ResourceList<Task> results = spec.apply(tasks);
		Assert.assertEquals(2, results.size());
	}

	@Test
	public void testFilterByMultiValuedAttributeNoMatch() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("projects", "id"), FilterOperator.EQ, 15L));
		ResourceList<Task> results = spec.apply(tasks);
		Assert.assertEquals(0, results.size());
	}

	@Test
	public void testFilterNotEquals() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("name"), FilterOperator.NEQ, "test1"));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(4, results.size());
	}

	@Test
	public void testFilterLE() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("id"), FilterOperator.LE, 1L));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(2, results.size());
	}

	@Test
	public void testFilterAnd() {
		QuerySpec spec = new QuerySpec(Task.class);
		FilterSpec spec1 = new FilterSpec(Arrays.asList("id"), FilterOperator.LE, 3L);
		FilterSpec spec2 = new FilterSpec(Arrays.asList("id"), FilterOperator.GT, 1L);
		spec.addFilter(FilterSpec.and(spec1, spec2));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(2, results.size());
	}

	@Test
	public void testFilterOr() {
		QuerySpec spec = new QuerySpec(Task.class);
		FilterSpec spec1 = new FilterSpec(Arrays.asList("id"), FilterOperator.LE, 1L);
		FilterSpec spec2 = new FilterSpec(Arrays.asList("id"), FilterOperator.GT, 3L);
		spec.addFilter(FilterSpec.or(spec1, spec2));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(3, results.size());
	}

	@Test
	public void testFilterNot() {
		QuerySpec spec = new QuerySpec(Task.class);
		FilterSpec spec1 = new FilterSpec(Arrays.asList("id"), FilterOperator.LE, 1L);
		spec.addFilter(FilterSpec.not(spec1));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(3, results.size());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testInvalidExpressionOperator() {
		QuerySpec spec = new QuerySpec(Task.class);
		FilterSpec spec1 = new FilterSpec(Arrays.asList("id"), FilterOperator.LE, 1L);
		spec.addFilter(new FilterSpec(FilterOperator.EQ, Arrays.asList(spec1)));
		spec.apply(tasks);
	}

	@Test
	public void testFilterLT() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("id"), FilterOperator.LT, 1L));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(1, results.size());
	}

	@Test
	public void testFilterGE() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("id"), FilterOperator.GE, 1L));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(4, results.size());
	}

	@Test
	public void testFilterGT() {
		QuerySpec spec = new QuerySpec(Task.class);
		spec.addFilter(new FilterSpec(Arrays.asList("id"), FilterOperator.GT, 1L));
		List<Task> results = spec.apply(tasks);
		Assert.assertEquals(3, results.size());
	}
}
