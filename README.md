Verification-Guided Refactoring Tool for refactoring implicit null checks in Java programs.

# Running
## Normal Usage
`java VGRTool <sourceDirPath> <refactoringModule>`

## With Gradle
### To Run
`./gradlew run --args="<sourceDirPath> <refactoringModule>"`

This will apply the provided refactorings to the Java project at <sourceDirPath>

### To Test
`./gradle test`

This will run the test suite.
