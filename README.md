# Themis

Themis is a search engine that was built to index and query the collection of documents from the 2019 [TREC Fair Ranking Track](https://fair-trec.github.io/). The collection was released on 2019-01-31 and contained 47 million documents from the Semantic Scholar Open Corpus with a total size of 108GB.

## Features

* Three retrieval models:
  * Boolean
  * Vector Space
  * Okapi
* Query expansion using deep learning models:
  * Statistical Dictionary [Glove](https://nlp.stanford.edu/projects/glove/).
  * Lexical Dictionary [WordNet](https://wordnet.princeton.edu/) ([extJWNL](http://extjwnl.sourceforge.net/) library).
* Pagerank ranking.
* Option to use stemming.
* Option to use a list of stopwords.
* Evaluation of the engine using a judgements file.

## Indexing

Indexing took 2h:30m on a i9-9900k with 64GB DDR4 RAM and 1TB SSD. Both stemming and stopwords were enabled. 235 partial indexes were created, these were then merged to create the final index.

## Evaluation

635 queries from a judgements file were used for the engine evaluation.

* The average of the average precision scores is between 70.03 and 71.69.
* The average of the nDCG scores is between 80.46 and 81.8.
* Average search time for finding the top 1 million documents was between 0.5s and 0.84s.

These numbers depend on the search parameters (retrieval model/query expansion/pagerank).

## Results

The '[results](results/)' folder contains a complete log of the indexing & evaluation results including a simple analysis of the graph structure of the citations.

## Compile

Java (8 or 11) & Maven 3.6

From the command line switch to the root directory of the project and run:

    mvn dependency:copy-dependencies
    mvn package

The first command should copy all .jar dependencies in the 'target/dependency' directory. The second command should build the final executable jar file in the 'target' folder.

## Run

### Running the project without a GUI

Write all code in the main function of the class gr.csd.uoc.hy463.themis.Themis. Compile the project then switch to the 'target' folder and run:

    java -Xmx32G -jar fairness-trec-2020-1.0-SNAPSHOT.jar

### Running the project with a GUI

The GUI gives us access to most of the needed functionality. Switch to the 'target' folder and run:

    java -Xmx32G -jar fairness-trec-2020-1.0-SNAPSHOT.jar gui

We need a 32G heap size only when recreating the index to ensure sufficient memory for the process.

## Query output

Currently the GUI displays only the top 10 results.

* When using the VSM/Okapi models, document fields are retrieved only for the top 50 results.
* When using the boolean model, document fields are retrieved for all results. Querying the collection is very fast only when the document ID is returned.

The above values are hardcoded, but the program can be easily modified to support any values.

## Configuration

Configuration options for indexing/searching/evaluation can be changed in the themis.config file. Some of the options can also be changed from the GUI.

Note that any changes to the config file are taken into account only when:

* A new index is created.
* An index is loaded.

## Screenshots

See [screenshots](screenshots/).

## Contributors

Initial parts of the project were contributed by [Panagiotis Padadakos](https://github.com/papadako).
