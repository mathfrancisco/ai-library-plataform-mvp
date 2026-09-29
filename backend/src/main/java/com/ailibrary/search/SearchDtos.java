package com.ailibrary.search;
import java.util.List;
import java.util.UUID;
public final class SearchDtos {
  private SearchDtos(){}
  public enum SearchMode { LEXICAL, SEMANTIC, HYBRID }
  public record SearchHit(UUID localBookId,String provider,String externalId,String title,List<String> authors,String coverUrl,String description,double score,String matchType){}
  public record DiscoveryRequest(String prompt){}
  public record DiscoveryPlan(String query,String language,Integer maxPages,List<String> categories){}
  public record DiscoveryResponse(DiscoveryPlan plan,List<SearchHit> results){}
}
