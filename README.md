# themis

themis is a search engine for scientific articles.

It was built to index and query the collection of documents from the [TREC Fair Ranking Track](https://fair-trec.github.io/). The entire collection consists of ~47 million articles, total size ~108GB. The collection indexed was the one released on 2019-01-31.

Other versions may or may not work with this engine and may require a few modifications.

## Features

* Two retrieval models: Vector space model & Okapi (BM25+).
* Query expansion using deep learning models:
  * Statistical Dictionary [Glove](https://nlp.stanford.edu/projects/glove/)
  * Lexical Dictionary [WordNet](https://wordnet.princeton.edu/) ([extJWNL](http://extjwnl.sourceforge.net/) library).
* Final score for a query is the combination of the score from a retrieval model plus a Pagerank score.
* Option to use stemming.
* Option to use a list of stopwords.
* Evaluation of the engine using a judgements file of ~600 queries.

## Indexing & Searching

The most important results are summarized below.

### Indexing

Indexing took ~3h:20m (single thread) on a workstation with 64GB RAM and a 256GB SSD. Both stemming and stopwords were enabled. The major steps were:

* Creating the index files: 94 partial indexes were created, these were then merged to create the final index.
* Calculation of the document weights for the Vector space model.
* Calculation of the Pagerank scores.

The total size of the final index was ~54GB and an additional ~42GB were required for the intermediate files. All necessary configuration options for this engine are in [meta.idx](results/meta.idx).

### Searching

The minimum retrieval time for 1M documents appears to be between 2.2s and 2.6s (single thread & retrieving only the necessary document properties). To achieve such timings the file that has the document metadata is always loaded as a memory mapped file.

However, searching requires a lot of memory especially when a query expansion model is used. This is expected since all results are returned (having few millions results is common).

### Evaluation

~600 queries from a judgements file were used for the evaluation of the engine.

The average of the average precision scores was ~0.7. The average of the nDCG scores was ~0.8. These numbers change slightly depending on the retrieval parameters (retrieval model/query expansion/pagerank).

Full results are [here](results/evaluation).

## Efficiency

A lot of effort went into optimizations:

* Fast creation of the index files.
* Low disk/mem usage during the creation of the index files.
* Fast search.
* Fast computation & low disk/mem usage during the calculation of the pagerank scores.

Searching is still considered slow and resource demanding mainly because all search results are returned.

## Results

The [results](results/) folder contains a complete log of the results including a simple analysis of the pagerank graph structure.

## Documentation

The [docs](doc/) folder contains reports about the indexing/searching/evaluation process, an overview of the fair ranking track, and detailed explanations of the algorithms used in this engine.

## TODO

Ideas for further improvements:

* Improve exception handling (properly close files, check for invalid arguments).
* Add support for showing paginated results in GUI. Currently only the top 10 results of each query are printed. Extending the GUI so that we can browse paginated results should be trivial since most of the required functions have already been implemented.
* Reduce memory requirements while searching (use different structures for holding the results, use a cutoff while sorting the results).

## Compile & Run

### Compile

This is a Maven project. Install Maven, then from the command line switch to the 'trec-search-engine' root folder and execute:

    mvn dependency:copy-dependencies
    mvn package

The first command should copy all .jar dependecies in the 'trec-search-engine/target/dependency' folder. The second command should build the final executable jar file in the 'trec-search-engine/target' folder.

### Run

#### Running the project without a GUI

Write the code that will be executed in the main function of the class gr.csd.uoc.hy463.themis.Themis. Compile the project as specified in the [compile](#Compile) section. Finally, switch to the 'trec-search-engine/target' folder and run the project as:

    java -Xmx45G -jar fairness-trec-2020-1.0-SNAPSHOT.jar

#### Running the project with a GUI

The GUI gives us access to most of the needed functionality. To enable the GUI, switch to the 'trec-search-engine/target' folder and run the project as:

    java -Xmx45G -jar fairness-trec-2020-1.0-SNAPSHOT.jar gui

### Using the config file

We can change the configuration options for indexing/searching/evaluation via the themis.config file. This has a direct effect on the resources required for those actions. Some of the parameters can also be changed from the GUI.

Note that any changes to the config file are taken into account only when:

* A new index is created.
* The index is loaded in memory.

## Screenshots

See [screenshots](screenshots/).

## Contributions

Parts of the project were contributed by [Panagiotis Padadakos](https://github.com/papadako).
