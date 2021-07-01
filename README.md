# themis

themis is a search engine for scientific articles.

It was built to index and query the collection of documents from the 2019 [TREC Fair Ranking Track](https://fair-trec.github.io/). The entire collection consists of 47 million articles, total size 108GB. The collection indexed was the one released on 2019-01-31.

Other releases may or may not work with this engine and may require a few modifications.

## Features

* Three retrieval models: Boolean, Vector Space, Okapi.
* Query expansion using deep learning models:
  * Statistical Dictionary [Glove](https://nlp.stanford.edu/projects/glove/).
  * Lexical Dictionary [WordNet](https://wordnet.princeton.edu/) ([extJWNL](http://extjwnl.sourceforge.net/) library).
* Pagerank analysis of the citations. Final document ranking is determined by both the retrieval model score and the Pagerank score.
* Option to use stemming.
* Option to use a list of stopwords.
* Evaluation of the engine using a judgements file.

## Indexing

Indexing took 2h:30m (single thread) on a i9-9900k with 64GB DDR4 RAM and 1GB SSD. Both stemming and stopwords were enabled. 235 partial indexes were created, these were then merged to create the final index.

The total size of the final index was 40.2GB and maximum disk usage during the process was 56.2GB.

## Evaluation

635 queries from a judgements file were used for the evaluation of the engine.

The average of the average precision scores is between 70.03 and 71.69. The average of the nDCG scores is between 80.46 and 81.8. These numbers depend on the search parameters (retrieval model/query expansion/pagerank).

Average search times (single thread) for 1M documents appear to be between 0.5s and 0.82s, again depending on the search parameters.

## Results

The [results](results/) folder contains a complete log of the indexing & evaluation results including a simple analysis of the pagerank graph structure of the citations.

## Efficiency

A lot of effort went into optimizations:

* Fast creation of the index files.
* Low disk/mem usage during the creation of the index files.
* Fast search.
* Fast computation & low disk/mem usage during the calculation of the pagerank scores.

## Compile

This is a Maven project. Install Maven, then from the command line switch to the 'trec-search-engine' root folder and execute:

    mvn dependency:copy-dependencies
    mvn package

The first command should copy all .jar dependecies in the 'trec-search-engine/target/dependency' folder. The second command should build the final executable jar file in the 'trec-search-engine/target' folder.

## Run

### Running the project without a GUI

Write the code that will be executed in the main function of the class gr.csd.uoc.hy463.themis.Themis. Compile the project as specified in the [compile](#Compile) section. Finally, switch to the 'trec-search-engine/target' folder and run the project as:

    java -Xmx32G -jar fairness-trec-2020-1.0-SNAPSHOT.jar

### Running the project with a GUI

The GUI gives us access to most of the needed functionality. To enable the GUI, switch to the 'trec-search-engine/target' folder and run the project as:

    java -Xmx32G -jar fairness-trec-2020-1.0-SNAPSHOT.jar gui

Currently the GUI displays only the top 10 results. Full document information is retrieved only for the top 50 results when the VSM/Okapi models are used and for all results when the boolean model is used. Extending the GUI to support different values should be trivial since the required functions have already been implemented.

## Configuration

Configuration options for indexing/searching/evaluation can be changed in the themis.config file. Some of the options can also be changed from the GUI.

Note that any changes to the config file are taken into account only when:

* A new index is created.
* An index is loaded in memory.

## Screenshots

See [screenshots](screenshots/).

## Contributions

Parts of the project were contributed by [Panagiotis Padadakos](https://github.com/papadako).
