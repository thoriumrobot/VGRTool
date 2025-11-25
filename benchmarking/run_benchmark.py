# pyright: basic
import os
import shutil
import sys


BENCHMARKING_DIR = "./benchmarking"  # Base directory for benchmarking inputs / outputs
DATASETS_DIR = (
    f"{BENCHMARKING_DIR}/datasets"  # Base directory for benchmarking datasets
)
DATASETS_CACHE_DIR = f"{DATASETS_DIR}/cache"  # Cache directory for NJR-1 dataset. Should remain unmodifed
DATASETS_REFACTORED_DIR = (
    f"{DATASETS_DIR}/refactored"  # Directory for datasets that will be modified
)

OUTPUT_DIR = f"{BENCHMARKING_DIR}/results"  # Directory for storing outputs
SRC_DIR = f"{BENCHMARKING_DIR}/sources"  # Directory for storing text files listing source files for each project
COMPILED_CLASSES_DIR = (
    f"{BENCHMARKING_DIR}/compiled_classes"  # Directory for storing compiled_classes
)

# Dependency jar files location
JARS_DIR = f"{BENCHMARKING_DIR}/jars"
ERRORPRONE_JAR_DIR = f"{JARS_DIR}/errorprone"
NULLAWAY_JAR_DIR = f"{JARS_DIR}/nullaway"
ANNOTATOR_JAR_DIR = f"{JARS_DIR}/annotator"
PROCESSOR_JARS = [
    {
        "PATH": f"{ERRORPRONE_JAR_DIR}/error_prone_core-2.38.0-with-dependencies.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/com/google/errorprone/error_prone_core/2.38.0/error_prone_core-2.38.0.jar",
    },
    {
        "PATH": f"{ERRORPRONE_JAR_DIR}/dataflow-errorprone-3.49.3-eisop1.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/io/github/eisop/dataflow-errorprone/3.49.3-eisop1/dataflow-errorprone-3.49.3-eisop1.jar",
    },
    {
        "PATH": f"{ERRORPRONE_JAR_DIR}/jFormatString-3.0.0.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/com/google/code/findbugs/jFormatString/3.0.0/jFormatString-3.0.0.jar",
    },
    {
        "PATH": f"{NULLAWAY_JAR_DIR}/nullaway-0.12.7.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/com/uber/nullaway/nullaway/0.12.7/nullaway-0.12.7.jar",
    },
    {
        "PATH": f"{NULLAWAY_JAR_DIR}/dataflow-nullaway-3.49.5.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/org/checkerframework/dataflow-nullaway/3.49.5/dataflow-nullaway-3.49.5.jar",
    },
    {
        "PATH": f"{NULLAWAY_JAR_DIR}/checker-qual-3.49.2.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/org/checkerframework/checker-qual/3.49.2/checker-qual-3.49.2.jar",
    },
    {
        "PATH": f"{ANNOTATOR_JAR_DIR}/annotator-core-1.3.15.jar",
        "DOWNLOAD_URL": "https://repo1.maven.org/maven2/edu/ucr/cs/riple/annotator/annotator-core/1.3.15/annotator-core-1.3.15.jar",
    },
]
PROCESSOR_JAR_PATHS = ":".join(map(lambda jar: jar["PATH"], PROCESSOR_JARS))

# NullAwayAnnotator Configuration
ANNOTATOR_OUT_DIR = f"{OUTPUT_DIR}/annotator"
SCANNER_CONFIG = f"{ANNOTATOR_OUT_DIR}/scanner.xml"
NULLAWAY_CONFIG = f"{ANNOTATOR_OUT_DIR}/nullaway.xml"
ANNOTATOR_CONFIG = f"{ANNOTATOR_OUT_DIR}/paths.tsv"
ANNOTATOR_JAR = f"{ANNOTATOR_JAR_DIR}/annotator-core-1.3.15.jar"

# Error Prone requires us to set the following --add-exports and--add-opens flags on JDK 16 and newer, due to "JEP 396: Strongly Encapsulate JDK Internals by Default".
ERROR_PRONE_EXPORTS = [
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
]


# The initialization stage for benchmarking
# Creates the necessary directories, removes old refactored datasets, confirms the existence of the necessary jar files, and downloads NJR-1 dataset if it has not been already.
def stage_zero():
    print("Beginning Stage Zero: Initialization...")

    print("Removing old refactored datasets...")
    shutil.rmtree(DATASETS_REFACTORED_DIR, ignore_errors=True)

    print("Initializing benchmarking folders and datasets")
    os.makedirs(SRC_DIR, exist_ok=True)
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(DATASETS_DIR, exist_ok=True)
    os.makedirs(DATASETS_CACHE_DIR, exist_ok=True)
    os.makedirs(COMPILED_CLASSES_DIR, exist_ok=True)
    os.makedirs(ERRORPRONE_JAR_DIR, exist_ok=True)
    os.makedirs(NULLAWAY_JAR_DIR, exist_ok=True)
    os.makedirs(ANNOTATOR_JAR_DIR, exist_ok=True)
    # Download datasets if they don't already exist
    if len(os.listdir(DATASETS_CACHE_DIR)) == 0:
        print("Downloading NJR-1 Dataset...")
        # Download NJR-1 Dataset
        res = os.system(
            f"wget https://zenodo.org/records/4632231/files/njr-1_dataset.zip -P {DATASETS_CACHE_DIR}"
        )

        if res != 0:
            print(f"Downloading datasets failed with exit code {res}. Exiting Program")
            sys.exit(1)

        # Extract zip file
        print("Extracting Datasets...")
        res = os.system(
            f"unzip {DATASETS_CACHE_DIR}/njr-1_dataset.zip -d {DATASETS_CACHE_DIR} && rm {DATASETS_CACHE_DIR}/njr-1_dataset.zip && mv {DATASETS_CACHE_DIR}/njr-1_dataset/* {DATASETS_CACHE_DIR} && rmdir {DATASETS_CACHE_DIR}/njr-1_dataset/"
        )
        if res != 0:
            print(
                f"Extracting downloaded datasets failed with exit code {res}. Exiting Program"
            )
            sys.exit(1)

    print("Creating copy of NJR-1 datasets cache to refactor...")
    res = os.system(f"cp -av {DATASETS_CACHE_DIR} {DATASETS_REFACTORED_DIR}")
    if res != 0:
        print(f"Copy dataset cache failed with exit code {res}. Exiting Program")
        sys.exit(1)

    print("Verifying necessary jar files...")
    for jar in PROCESSOR_JARS:
        if not os.path.exists(jar["PATH"]):
            print(
                f"Warning: Missing necessary jar file at {jar["PATH"]}. Downloading jar..."
            )
            res = os.system(f"wget {jar["DOWNLOAD_URL"]} -O {jar["PATH"]}")

            if res != 0:
                print(
                    f"Downloading necessary jar failed with exit code {res}. Exiting Program"
                )
                sys.exit(1)
            print(f"Succesfully downloaded and saved jar file at {jar["PATH"]}.")

    print("Benchmarking Stage Zero Completed\n")


def run():
    """
    Runs the full benchmarking routine for every dataset in the NJR-1 dataset collection and then summarizes the results.
    """
    stage_zero()


run()
