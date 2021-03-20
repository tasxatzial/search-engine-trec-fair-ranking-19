# Results

Here you'll find various logs related to the creation and evaluation of the engine.

* [indexing.txt](indexing.txt): The output of the index build process.
* [meta.idx](meta.idx): Configuration options for this engine:

  * **avgdl**: Average document length (in tokens). Used in Okapi retrieval model scoring function.
  * **max_doc_size**: Maximum document size (bytes). Obsolete.
  * **pagerank_damping**: Damping factor for the pagerank algorithm.
  * **use_stopwords**: true/false depending on whether a list of stopwords has been used.
  * **use_stemmer**: true/false depending on whether the tokens have been stemmed.
  * **articles**: total number of articles in the collection.
  * **pagerank_threshold**: Stopping criterion for the pagerank algorithm.
  * **timestamp**: When this index was build.
* [evaluation](evaluation/): Evaluation results for different search options.
* [citations_graph](citations_graph/): Simple analysis of the pagerank graph structure of the citations.
