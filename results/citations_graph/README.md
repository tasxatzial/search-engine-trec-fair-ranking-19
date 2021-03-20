# Graph analysis

Here you'll find a simple analysis of the pagerank graph structure of the citations. IN/OUT edges refer to node incoming/outgoing edges. Citations and edges are used interchangeably.

* **[citations_graph_stats](citations_graph_stats)**:
  * Documents: Total number of nodes.
  * Sinks: Number of nodes that do not have any OUT edges.
  * Sinks +(self references): Number of nodes that do not have any OUT edges. Includes nodes that have loop OUT edges.
  * In edges -(self references + multiple references): Number of IN edges. Does not include loop IN edges. Multiple same IN edges
  are counted 1 time.
  * In edges: Number of IN edges.
  * Out edges -(self references + multiple references): Number of OUT edges. Does not include loop OUT edges. Multiple same OUT
  edges are counted 1 time.
  * Out edges: Number of OUT edges.
  * Duplicate In edges -(self references): Number of unnessecary IN edges. Does not include loop IN edges.
  * Documents with duplicate In edges -(self references): Number of nodes that have unnessecary IN edges.
  Does not include nodes that do not have unnessecary IN edges but have loop IN edges.
  * Duplicate Out edges -(self references): Number of unnessecary OUT edges. Does not include loop OUT edges.
  * Documents with duplicate Out edges -(self references): Number of nodes that have unnessecary OUT edges.
  Does not include nodes that do not have unnessecary OUT edges but have loop OUT edges.
  * Self referencing documents via In edges: Number of nodes that have loop IN edges.
  * Self referencing In edges: Number of loop IN edges.
  * Self referencing documents via Out edges: Number of nodes that have loop OUT edges.
  * Self referencing Out edges: Number of loop OUT edges.
  * In edges which are not listed in the Out edges of the source node.
  * Out edges which are not listed in the In edges of the source node.
* **[citations_not_found_graph_stats](citations_not_found_graph_stats)**:
  * Documents: Total number of nodes.
  * Total not found In edges -(multiple references): IN citations that are not included in the collection. Multiple references of
  the same IN citation in an article are counted 1 time. Multiple references of the same IN citation across different articles
  are counted separately.
  * Unique not found In edges -(multiple references): IN citations that are not included in the collection. Multiple references of
  the same IN citation in the collection are counted 1 time.
  * Total not found Out edges -(multiple references): OUT citations that are not included in the collection. Multiple references of
  the same OUT citation in an article are counted 1 time. Multiple references of the same OUT citation across different articles
  are counted separately.
  * Unique not found Out edges -(multiple references): OUT citations that are not included in the collection. Multiple references of
  the same OUT citation in the collection are counted 1 time.
* **[in_edges](in_edges)**: Histogram of the number of IN citations.
* **[in_edges_not_found](in_edges_not_found)**: Histogram of the number of IN citations that are not found in the collection.
Multiple same IN citations in an article are counted 1 time.
* **[in_edges_true](in_edges_true)**: Histogram of the number of IN citations. Multiple references of the same IN citation in
an article are counted 1 time. Loop IN citations are not counted.
* **[out_edges](out_edges)**: Histogram of the number of OUT citations.
* **[out_edges_not_found](out_edges_not_found)**: Histogram of the number of OUT citations that are not found in the collection.
Multiple same OUT citations in an article are counted 1 time.
* **[out_edges_true](out_edges_true)**: Histogram of the number of OUT citations. Multiple references of the same OUT citation in
an article are counted 1 time. Loop OUT citations are not counted.
