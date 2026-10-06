# TPQ Library

This library provides the core building blocks for loading graph relations, applying temporal operators, and evaluating query plans over data represented as node pairs with lists of windows. It was used to put in practice the theory in the thesis:

> Petar Grigorov. *Querying Graphs with Temporal Validity and Uncertainty*. Unpublished Bachelor's thesis, Free University of Bozen-Bolzano, October 2026.

## Overview

The library includes:

- relation loading from text files and SQL tables
- temporal interval and window utilities
- relational operators such as `Read`, `Select`, `Exists`, `Converse`, `Stretch`, `TemporalJoin`, and `Union`
- query evaluation strategies for different normalization and coalescing modes
- JUnit-based tests for query evaluation

The code is organized around a small Java API centered on `Graph`, `ResultMap`, `Query`, and the operator hierarchy under `Operators`.

## Project Structure

```text
.
├── pom.xml
├── src/
│   ├── main/java/
│   │   ├── Data/
│   │   │   ├── Graph.java
│   │   │   ├── Query.java
│   │   │   ├── ResultMap.java
│   │   │   └── Window.java
│   │   ├── Operators/
│   │   │   ├── Binary/
│   │   │   ├── Nullary/
│   │   │   ├── Unary/
│   │   │   ├── AbstractOperator.java
│   │   │   └── Operator.java
│   │   └── Utils/
│   │       ├── Interval.java
│   │       ├── Metrics.java
│   │       ├── NoMetrics.java
│   │       └── Utils.java
│   └── test/
│       ├── java/
│       └── resources/
└── .gitignore
```

## Getting Started

### Prerequisites

- Java 17 or newer
- Maven 3.8+

### Build and test

```bash
mvn test
```

To build the project artifact:

```bash
mvn package
```

## Example usage

```java
import Data.Graph;
import Data.Query;
import Operators.Nullary.Read;
import Operators.Unary.Converse;
import Operators.Unary.Exists;
import Operators.Unary.Select.Atom;
import Operators.Unary.Select.Select;
import Operators.Unary.Stretch.VStretch;
import Operators.Binary.TemporalJoin;
import Utils.Interval;

Graph conf = new Graph();
conf.fetchTxtRelations("src/test/resources/conferences_1");

Operator attends = new Read("attends", conf);

ResultMap result = Query.eval(
    new TemporalJoin(
        new VStretch(
            new Converse(
                new Select(
                    attends,
                    new Atom(Atom.Node.N2, "ISWC")
                )
            ),
        new Interval(3, 4)),
        new Exists(
            new Select(
                attends,
                new Atom(Atom.Node.N2, "positive")
            )
        )
    ),
    Query.QueryStrategy.MINIMAL,
    false,
    false
);
```

## Query model

The library models temporal relations as collections of temporal windows. Each result map stores entries keyed by a node pair and associated windows. Query evaluation is performed by recursively evaluating operators that manipulate these structures.

The project also supports different strategies for when normalization and coalescing are applied. Some include:

- final normalization/coalescing
- pre-join normalization/coalescing
- no normalization/coalescing

## Tests

The repository includes a test suite under `src/test/java` that exercises graph loading, operator behavior, normalization, and query evaluation.

Example test files include:

- `QueryTest.java`
- `OperatorTest.java`
- `GraphReadTest.java`
- `NormalizationTest.java`
- `CoalescingTest.java`
