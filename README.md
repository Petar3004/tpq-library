# TPQ Library

A Java library for evaluating temporal queries over graph-style relations using interval-based windows and relational operators.

This project provides the core building blocks for loading graph relations, applying temporal operators, and evaluating query plans over data represented as node pairs with time intervals.

## Overview

The library includes:

- relation loading from text files and SQL tables
- temporal interval and window utilities
- relational operators such as `Read`, `Select`, `Exists`, `Converse`, `Stretch`, `TemporalJoin`, and `Union`
- query evaluation strategies for different normalization and coalescing modes
- JUnit-based tests covering representative query scenarios

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

var attends = new Read("attends", conf);

var result = Query.eval(
    new TemporalJoin(
        new VStretch(new Converse(new Select(attends, new Atom(Atom.Node.N2, "ISWC"))), new Interval(3, 4)),
        new Exists(new Select(attends, new Atom(Atom.Node.N2, "positive")))
    ),
    Query.QueryStrategy.MINIMAL,
    false,
    false
);
```

## Query model

The library models temporal relations as collections of node-pair windows. Each relation stores entries keyed by a node pair and associated time intervals. Query evaluation is performed by composing operators that manipulate these structures while preserving temporal semantics.

The project also supports strategies for:

- final normalization
- final coalescing
- maximal query optimization

## Tests

The repository includes a test suite under `src/test/java` that exercises graph loading, operator behavior, normalization, and query evaluation.

Example test files include:

- `QueryTest.java`
- `OperatorTest.java`
- `GraphReadTest.java`
- `NormalizationTest.java`
- `CoalescingTest.java`

## License

This project does not currently declare a license in `pom.xml` or the repository root. If this repository is intended for public distribution, consider adding an appropriate open-source license such as MIT or Apache 2.0.

## Notes

This README is intentionally concise and focused on the current project structure and usage patterns. As the library evolves, you may want to add:

- a more detailed API reference
- usage examples for specific operators
- benchmark or performance notes
- contribution guidelines

