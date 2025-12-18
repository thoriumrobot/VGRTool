# Benchmarking

# Stage Zero: Initiailization

The first stage of benchmarking involves downloading all the necessary files
required, i.e. Jar files and the NJR-1 Dataset.

## Dependencies:

In order for benchmarking to run, the jar files for all the following
dependencies must be located in the `JARS_DIR` directory, in their respective
sub-folder (i.e. `JARS_DIR/errorprone`, `JARS_DIR/nullaway`, and
`JARS_DIR/annotator`)

## Note: Different versions of the following dependencies may not be compatable. The newest versions of each project that are confirmed to work together are:

- Error-Prone-Core: 2.38.0
- Dataflow-Errorprone: 3.49.3
- jFormatString: 3.0.0
- Nullaway-Core: 0.12.7
- Dataflow-Nullaway: 3.49.5
- Checker-Qual: 3.49.2
- Annotator-Core: 1.3.15

### ErrorProne

Error Prone is a static analysis tool for Java that catches common programming
mistakes at compile-time. It is used as the basis of NullAway and NullAway
Annotator. Requires the Error-Prone-Core and Dataflow-Errorprone artifacts

- [Error-Prone](https://mvnrepository.com/artifact/com.google.errorprone/error_prone_core)
  - Static analysis tool for Java that catches common programming mistakes at
    compile-time
- [Dataflow-Errorprone](https://mvnrepository.com/artifact/io.github.eisop/dataflow-errorprone)
  - Dataflow framework based on the javac compiler. This artifact also contains
    the dependencies it requires.
- [JFormat String](https://mvnrepository.com/artifact/com.google.code.findbugs/jFormatString)
  - jFormatString for Findbugs; Required for ErrorProne

### NullAway

A tool to help eliminate NullPointerExceptions (NPEs) in Java code, used to
detect NPEs in the dataset.

Requires the Nullaway and Dataflow-Nullaway artifacts to run. The source code
being analyzed must be annotated beforehand, either by-hand or automatically
using a tool like NullAwayAnnotator.

- [Nullaway](https://mvnrepository.com/artifact/com.uber.nullaway/nullaway)
  - A fast annotation-based null checker for Java
- [Dataflow-Nullaway](https://mvnrepository.com/artifact/org.checkerframework/dataflow-nullaway)
  - Dataflow framework based on the javac compiler. This artifact also contains
    the dependencies it requires.

### [NullAwayAnnotator](https://github.com/ucr-riple/NullAwayAnnotator)

A tool that automatically infers nullability types in the given source code and
injects the corresponding annotations to pass NullAway checks.

NullAwayAnnotator is required in order to automatically annotate the datasets
before NullAway can process them.

- [Annotator-Core](https://mvnrepository.com/artifact/edu.ucr.cs.riple.annotator/annotator-core)
  - A tool that automatically fixes source code to pass NullAway checks.
- [Checker-Qual](https://mvnrepository.com/artifact/org.checkerframework/checker-qual/)
  - checker-qual contains annotations (type qualifiers) that a programmer writes
    to specify Java code for type-checking by the Checker Framework.

# Stage One: Annotation

Stage One is the largest stage in terms of time consumption and computation. It
involves running NullAwayAnnotator on the entire NJR-1 dataset in order to
prepare it for refactoring, as well as to get an accurate count of the number of
NullAway errors in the original programs, refactoring every program using VGR,
and finally re-running annotator to get an updated error count. This cycle
(annotate->refactor->annotate) is completed for each program in NJR-1
sequentially.
