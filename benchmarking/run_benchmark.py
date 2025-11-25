# pyright: basic
import argparse
from datetime import datetime
import os
import re
import shutil
import subprocess
import sys

BENCHMARKING_DIR = "./benchmarking"  # Base directory for benchmarking inputs / outputs
DATASETS_DIR = (
    f"{BENCHMARKING_DIR}/datasets"  # Base directory for benchmarking datasets
)
DATASETS_CACHE_DIR = f"{DATASETS_DIR}/cache"  # Cache directory for NJR-1 dataset. Should remain unmodifed
DATASETS_REFACTORED_DIR = (
    f"{DATASETS_DIR}/refactored"  # Directory for datasets that will be modified
)
DATASETS_REFACTORED_SAVE_DIR = f"{DATASETS_DIR}/old-runs/refactored"  # Directory for datasets that will be modified

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

results = []
DEBUG = False


# The initialization stage for benchmarking
# Creates the necessary directories, saves old refactored datasets, confirms the existence of the necessary jar files, and downloads NJR-1 dataset if it has not been already.
def stage_zero():
    print("Beginning Stage Zero: Initialization...")

    save_dir = f"{DATASETS_REFACTORED_SAVE_DIR}/{datetime.now():%Y-%m-%d_%H:%M:%S}"
    print(f"Saving existing refactored datasets to {save_dir}")
    if os.path.exists(DATASETS_REFACTORED_DIR):
        try:
            shutil.move(DATASETS_REFACTORED_DIR, save_dir)
        except:
            print(
                f"Fatal Error: Could not save existing refactored datasets to {save_dir}. Move operation failed. Exiting program."
            )
        sys.exit(1)

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


def stage_one():
    """
    Runs the full benchmarking routine (Annotate -> Count Errors -> Refactor -> Annotate -> Count Errors) for every dataset in the NJR-1 dataset collection and then summarizes the results.
    """
    datasets_list = os.listdir(DATASETS_REFACTORED_DIR)

    for dataset in datasets_list:
        print(f"Benchmarking {dataset}...")
        os.makedirs(f"{OUTPUT_DIR}/{dataset}", exist_ok=True)

        ## Step 1: Annotate dataset
        stage_one_annotate(dataset)

        ## Step 2: Count initials errors
        old_err_count = stage_one_count_errors(dataset)

        ## Step 3: Refactor dataset
        stage_one_refactor(dataset)

        ## Step 4: Count errors after refactoring
        new_err_count = stage_one_count_errors(dataset)

        print(
            f"Finished benchmarking {dataset}. Errors: {old_err_count} --> {new_err_count}\n"
        )
        results.append(
            {
                "benchmark": dataset,
                "initial_error_count": old_err_count,
                "refactored_error_count": new_err_count,
            }
        )
    print(f"Finished benchmarking {dataset}")
    print("Results:")
    for res in results:
        print(
            f"Dataset: {res['benchmark']}\nInitial Errors: {res['initial_error_count']}\nRefactored Errors: {res['refactored_error_count']}"
        )

    stage_one_save_results_to_csv()
    return


# Utility Functions


def stage_one_annotate(dataset: str):
    """
    Runs NullAwayAnnotator on the passed dataset in order to prepare it for
    refactoring
    """

    print(f"Annotating {dataset}...")

    # Create config files
    os.makedirs(ANNOTATOR_OUT_DIR, exist_ok=True)
    with open(f"{ANNOTATOR_CONFIG}", "w") as config_file:
        _ = config_file.write(f"{NULLAWAY_CONFIG}/\t{SCANNER_CONFIG}\n")

    # Clear annotator output folder (required for annotator to run)
    shutil.rmtree(ANNOTATOR_OUT_DIR + "/0", ignore_errors=True)

    build_cmd = " ".join(get_build_cmd(dataset))
    cwd = os.getcwd()

    annotate_cmd: list[str] = [
        "java",
        "-jar",
        ANNOTATOR_JAR,
        # Absolute path of an Empty Directory where all outputs of AnnotatorScanner and NullAway are serialized.
        "-d",
        ANNOTATOR_OUT_DIR,
        # Command to run Nullaway on target; Should be executable from anywhere
        "-bc",
        f'"cd {cwd} && {build_cmd}"',
        # Path to a TSV file containing value of config paths
        "-cp",
        ANNOTATOR_CONFIG,
        # Fully qualified name of the @Initializer annotation.
        "-i",
        "com.uber.nullaway.annotations.Initializer",
        # Custom @Nullable annotation.
        "-n",
        "javax.annotation.Nullable",
        # Checker name to be used for the analysis.
        "-cn",
        "NULLAWAY",
        # Max depth to traverse as part of the analysis search
        "--depth",
        "10",
    ]
    res = subprocess.run(annotate_cmd, text=True, capture_output=True)
    if res.returncode != 0:
        print(
            f"Annotation failed with exit code {res.returncode} for dataset {dataset}"
        )
        return

    output_log_path = f"{OUTPUT_DIR}/{dataset}/annotator.txt"
    with open(output_log_path, "w") as f:
        f.write(f"CMD:\n\t{" ".join(annotate_cmd)}\n")
        f.write(f"STDOUT:\n\t{res.stdout}\n")
        f.write(f"STDERR:\n\t{res.stderr}\n")

    if DEBUG:
        print(
            f"Command used to annotate dataset {dataset}: \n\t{" ".join(annotate_cmd)}\n"
        )

    return


def stage_one_refactor(dataset: str):
    """
    Runs VGR on the passed dataset
    """

    print(f"Refactoring {dataset}...")

    output_file = f"{OUTPUT_DIR}/{dataset}/refactoring.txt"
    dataset_path = f"{DATASETS_REFACTORED_DIR}/{dataset}"

    refactor_cmd: list[str] = ["./gradlew", "run", f"--args={dataset_path} All"]

    with open(output_file, "w") as f:
        res = subprocess.run(
            " ".join(refactor_cmd) + f" &> {output_file}", shell=True, check=False
        )

    if res != 0:
        print(
            f"Running VGRTool failed with exit code {res} for dataset {dataset}. See {output_file} for more details."
        )

    if DEBUG:
        print(
            f"Refactor Command for dataset {dataset}: : {' '.join(refactor_cmd)} &> {output_file}"
        )
    return


def stage_one_count_errors(dataset: str):
    """Builds the passed datsets and counts NullAway errors during the build process."""
    build_cmd = " ".join(get_build_cmd(dataset))
    log_file = (
        f"{OUTPUT_DIR}/{dataset}/error_count_log-{datetime.now():%Y-%m-%d_%H:%M:%S}.txt"
    )
    output_file = (
        f"{OUTPUT_DIR}/{dataset}/error_count-{datetime.now():%Y-%m-%d_%H:%M:%S}.txt"
    )

    # Build the dataset and redirect all outputs to a log file
    with open(log_file, "w") as f:
        res = subprocess.run(
            build_cmd, stdout=f, stderr=subprocess.STDOUT, check=False, text=True
        )
        if res != 0:
            print(
                f"Building dataset {dataset} failed with exit code {res}. Skipping dataset..."
            )
            return

    # Read the log file and count occurrences of NullAway errors
    with open(log_file, "r") as f:
        error_count = len(re.findall(r"error: \[NullAway\]", f.read()))

    with open(output_file, "a") as f:
        f.write(f"Error Count: {error_count}\n")

    if DEBUG:
        print(f"Number of errors found for dataset {dataset}: {error_count}")
    return error_count


def get_build_cmd(dataset: str):
    """
    Constructs the full 'javac' build command used to compile the passed dataset.
    """
    lib_dir = f"{DATASETS_REFACTORED_DIR}/{dataset}/lib"
    src_file = get_source_files(dataset)
    plugin_options = get_plugin_options(dataset)

    build_cmd: list[str] = ["javac"]
    build_cmd += ERROR_PRONE_EXPORTS
    build_cmd += [
        "-d",
        f"{COMPILED_CLASSES_DIR}",
        "-cp",
        f"{lib_dir}:{ANNOTATOR_JAR}",
        "-XDcompilePolicy=simple",
        "--should-stop=ifError=FLOW",
        "-processorpath",
        f"{PROCESSOR_JARS}",
        f"'{plugin_options}'",
        "-Xmaxerrs",
        "0",
        "-Xmaxwarns",
        "0",
        f"@{src_file}",
    ]
    return build_cmd


def get_source_files(dataset):
    find_srcs_command = [
        "find",
        f"{DATASETS_REFACTORED_DIR}/{dataset}/src",
        "-name",
        "*.java",
    ]
    src_file = f"{SRC_DIR}/{dataset}.txt"
    with open(src_file, "w") as f:
        _ = subprocess.run(find_srcs_command, stdout=f)
    return src_file


def get_plugin_options(dataset: str):
    """
    Generates the -Xplugin:ErrorProne option string, including a dynamically generated list of packages to annotate.
    """
    dataset_path = f"{DATASETS_REFACTORED_DIR}/{dataset}"
    find_pkgs_command = (
        f"find {dataset_path}"
        + " -name '*.java' -exec awk 'FNR==1 && /^package/ {print $2}' {} + | sed 's/;//' | sort -u | tr '\n\r' ',' | sed 's/,,/,/g' | sed 's/,$//'"
    )

    pkgs = subprocess.run(
        find_pkgs_command, shell=True, capture_output=True
    ).stdout.decode("utf-8")

    # Split the annotated packages
    annotated_pkgs = pkgs.strip()
    annotated_pkgs_arg = f"-XepOpt:NullAway:AnnotatedPackages={annotated_pkgs}"

    return f"-Xplugin:ErrorProne \
             -XepDisableAllChecks \
             -Xep:AnnotatorScanner:ERROR \
             -XepOpt:AnnotatorScanner:ConfigPath={SCANNER_CONFIG}  \
             -Xep:NullAway:ERROR \
             -XepOpt:NullAway:SerializeFixMetadata=true \
             -XepOpt:NullAway:FixSerializationConfigPath={NULLAWAY_CONFIG} \
             {annotated_pkgs_arg}"


def stage_one_save_results_to_csv():
    save_dir = f"{DATASETS_REFACTORED_SAVE_DIR}/{datetime.now():%Y-%m-%d_%H:%M:%S}"

    print(f"Saving existing refactored datasets to {save_dir}")
    if os.path.exists(DATASETS_REFACTORED_DIR):
        try:
            os.makedirs(DATASETS_REFACTORED_SAVE_DIR, exist_ok=True)
            shutil.move(DATASETS_REFACTORED_DIR, save_dir)
        except Exception as e:
            print(
                f"Fatal Error: Could not save existing refactored datasets. Move operation failed with error code: {e}. Exiting program."
            )
            sys.exit(1)


def run():
    """
    Runs the full benchmarking routine for every dataset in the NJR-1 dataset collection and then summarizes the results.
    """
    stage_zero()
    stage_one()


def main():
    """Main entry point of the script."""
    global DEBUG
    argparser = argparse.ArgumentParser(description="Runs benchmark.")
    argparser.add_argument(
        "--debug", action="store_true", help="Enabling debugging statements."
    )
    args = argparser.parse_args()
    DEBUG = args.debug

    stage_zero()
    stage_one()


if __name__ == "__main__":
    main()
