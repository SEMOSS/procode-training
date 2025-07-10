package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import tqmc.domain.base.Filter.Comparison;

public class GetListPayload {
  public List<Sort> sort;
  public List<Filter> filter;
  public Pagination pagination;
  @JsonIgnore public Map<String, Map<Comparison, List<String>>> collatedFilters;

  public GetListPayload() {}

  public List<Sort> getSort() {
    return sort;
  }

  public void setSort(List<Sort> sort) {
    this.sort = sort;
  }

  public List<Filter> getFilter() {
    return filter;
  }

  public void setFilter(List<Filter> filter) {
    this.filter = filter;
  }

  public Map<String, Map<Comparison, List<String>>> getCollatedFilters() {
    return collatedFilters;
  }

  public void setCollatedFilters(Map<String, Map<Comparison, List<String>>> collatedFilters) {
    this.collatedFilters = collatedFilters;
  }

  public Pagination getPagination() {
    return pagination;
  }

  public void setPagination(Pagination pagination) {
    this.pagination = pagination;
  }
}
