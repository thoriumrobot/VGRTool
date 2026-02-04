#!/bin/bash
javac -J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
        -J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
        -J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
        -J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
        -d ./benchmarking/compiled_classes \
        -cp ./benchmarking/datasets/refactored/url89849c8c5b_timmolter_XChart_tgz-pJ8-org_knowm_xchart_standalone_Example1J8/lib:./benchmarking/jars/annotator/annotator-core-1.3.15.jar \
        -XDcompilePolicy=simple \
        --should-stop=ifError=FLOW \
        -processorpath ./benchmarking/jars/errorprone/error_prone_core-2.35.1-with-dependencies.jar:./benchmarking/jars/errorprone/dataflow-errorprone-3.49.3-eisop1.jar:./benchmarking/jars/errorprone/jFormatString-3.0.0.jar:./benchmarking/jars/nullaway/nullaway-0.12.7.jar:./benchmarking/jars/nullaway/dataflow-nullaway-3.49.5.jar:./benchmarking/jars/nullaway/checker-qual-3.49.2.jar:./benchmarking/jars/annotator/annotator-core-1.3.15.jar \
        '-Xplugin:ErrorProne -XepDisableAllChecks -Xep:AnnotatorScanner:ERROR -XepOpt:AnnotatorScanner:ConfigPath=./benchmarking/outputs/annotator/scanner.xml -Xep:NullAway:ERROR -XepOpt:NullAway:SerializeFixMetadata=true -XepOpt:NullAway:FixSerializationConfigPath=./benchmarking/outputs/annotator/nullaway.xml -XepOpt:NullAway:AnnotatedPackages=' \
        -Xmaxerrs 0 -Xmaxwarns 0 @./benchmarking/sources/url89849c8c5b_timmolter_XChart_tgz-pJ8-org_knowm_xchart_standalone_Example1J8.txt
