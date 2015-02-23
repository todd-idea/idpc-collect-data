# IDEA Data Portal CLI - Collect Report Data

This project provides an example application that pulls data from the IDEA Data Portal. It is a
Groovy-based application that uses Gradle to build.

By providing it a valid application name and key (via command line arguments), you can create a CSV
of student ratings data (the data from diagnostic surveys). In this example, it dumps the
Overall Ratings (the mean and t-scores ... both raw and adjusted) along with the survey subject
and course information.

### Example Data

Survey Subject | Course                                | Mean (Raw) | T-Score (Raw) | Mean (Adjusted) | T-Score (Adjusted)
---------------|---------------------------------------|------------|---------------|-----------------|-------------------
Ken Ryalls     | Calculus I (MATH 301)                 | 3.7        | 44.5          | 3.7             | 44
Pat Sullivan   | Intro to Music (MUS 101)              | 4.4        | 55.5          | 4.1             | 50
Steve Benton   | International Education (ED 714)      | 3.9        | 47            | 3.9             | 47
Jake Glover    | Insurance (FIN 460)                   | 4.9        | 62.5          | 4.5             | 57
Dan Li         | Intro to Adult Education (ED 780)     | 4.3        | 54.5          | 4.3             | 54

## Building

To build this project, you will need Gradle installed and have access to the required dependencies (if connected to the
internet, they will be downloaded for you).

Once Gradle is setup, you can run the following to get the dependencies downloaded and the code compiled.
```
gradle build
```

## Installing

You can install the application using Gradle as well. You can run the following to install it (relative to the project root)
in build/install/idpc-collect-data.
```
gradle installDist
```

## Running

Once installed, you can run using the following.
```
cd build/install/idpc-collect-data/bin
./idpc-collect-data -a "TestClient" -k "ABCDEFG1234567890"
```
This will run against the IDEA Data Portal (rest.ideasystem.org) and spit out CSV (comma separated value) data that can
be imported into a spreadsheet (LibreCalc or Microsof Excel).

There are some command line options that you can use as well.
- h (host): This can configure the application to query a different server
- p (port): This can configure the application to query a different port on the server
- v (verbose): This can turn on more verbose output; this dumps a lot more information if you want to see what is being returned