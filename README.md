# themis

## Description

themis is a search engine for scientific articles, built to index and search the collection of documents from the
[TREC Fair Ranking Track](https://fair-trec.github.io/).

The entire collection consists of ~47 million articles, total size ~108GB. A small sample can be found
[here](/samples/sample-S2-records).

## Features

* Two retrieval models: Vector space model & Okapi (BM25+).
* Query expansion using two models: Statistical Dictionary [Glove](https://nlp.stanford.edu/projects/glove/) &
Lexical Dictionary [WordNet](https://wordnet.princeton.edu/) (using [extJWNL](http://extjwnl.sourceforge.net/)).
* Calculation of Pagerank scores which can be combined with the scores from the retrieval
models.
* Option to use stemming.
* Option to use a list of stopwords.
* Evaluation of the seach engine using a judgements file of ~600 queries.

## Indexing & Searching

A complete report can be found in [report-phaseB](/doc/report-phaseB.pdf) (in greek).
Results are in the [evaluation](/results/) folder. The most important results
are summarized below.

### Indexing

Indexing took ~3h:20m in a 6 core cpu (using 1 thread) with 64GB RAM and a 256GB SSD. Both stemming and stopwords were enabled.
The major steps in the process included:

* Creating the index files: 94 partial indexes were created, these were then merged to create the final index.
* Calculation of the document weights for the Vector space model.
* Calculation of the Pagerank scores.

The total size of the final index was ~54GB and an additional ~42GB were required for the intermediate files.

### Searching

The minimum retrieval time for 1M documents appears to be between 2.2s and 2.6s (using 1 thread & retrieving
only the necessary document properties).

### Evaluation

~600 queries from a [judgements file](/samples/fair-TREC-training-sample.json) were used for the evaluation of the engine.

The average of the average precision scores was ~0.7. The average of the nDCG scores was ~0.8. These numbers change
slightly depending on the retrieval parameters (retrieval model/query expansion/pagerank).

### Pagerank

Convergence was achieved in 45 iterations using threshold = 1e-8 and damping factor = 0.85. Iterations completed
in ~7m.

## Compile & Run

### Compile

This is a Maven project. Install Maven, then from the command line switch to the 'trec-search-engine' root folder and execute:

    mvn dependency:copy-dependencies
    mvn package

The first command should copy all .jar dependecies in the 'trec-search-engine/target/dependency' folder. The second command
should build the final executable jar file in the 'trec-search-engine/target' folder.

### Run

#### Running the project without a GUI

Write the code that will be executed in the main function of the class gr.csd.uoc.hy463.themis.Themis.
Then compile the project as specified in the [compile](#Compile) section. Finally, switch to the 'trec-search-engine/target'
folder and run the project as:

    java -Xmx45G -jar hy463-fairness-trec-2020-1.0-SNAPSHOT.jar

#### Running the project with a GUI

The GUI gives us access to most of the needed functionality. It has some limitations though as explained in the following
section. To enable the GUI, switch to the 'trec-search-engine/target' folder and run the project as:

    java -Xmx45G -jar hy463-fairness-trec-2020-1.0-SNAPSHOT.jar gui

### GUI limitations

Only the top 10 results of each query are printed. Extending the GUI so that we can browse paginated results should be
trivial since most of the required functions are already present.

### Using the config file

We can change the parameters used in indexing/searching/evaluation via the themis.config file. Some of them can also
be changed from the GUI.

Any changes to the config file are taken into account only when:

* A new index is created.
* The index is loaded into memory.

### Requirements

Besides the required disk space which was already mentioned in the previous sections, the main concern is to keep
the memory usage as low as possible.

* The file that has the document metadata is always loaded into memory during indexing/search/evaluation to speed up the process.
* During the indexing we can increase/decrease the memory requirements by changing the themis.config parameter
PARTIAL_INDEX_MAX_DOCS_SIZE. This will also have an effect on the creation/merging time of the partial indexes.
* During search/evaluation, we cannot manually change the memory requirements. The 64GB were sufficient for every query
in the judgements file, although in some cases
more than 32GB were required especially when we've used a query expansion model. There is definitely room
for improvements here.
* Pagerank computations have been optimized as much as possible. At this point they use ~21GB of
memory.

## Screenshots

See [screenshots](screenshots/).

## Contributions

Parts of the project were contributed by [Panagiotis Padadakos](https://github.com/papadako).
