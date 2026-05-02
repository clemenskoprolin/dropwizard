# Dropwizard documentation

## J2K evaluation notes

This fork also contains a Java-to-Kotlin conversion evaluation harness:

- [J2K evaluation summary](summary.md)
- [J2K edge-case dataset](edge-cases.md)
- [Headless IntelliJ runner](../tools/headless-j2k-runner/)

## Building locally

Create and enter the Python virtual environment:

    # virtualenv .
    # source ./bin/activate

Install [Sphinx](http://sphinx-doc.org) and all required dependencies:

    # pip install -r requirements.txt

Build the static documentation and open it in your browser:

    # make html
    # open target/html/index.html

Build the documentation and automatically build them on any change:

    # make livehtml
    # open http://127.0.0.1:8000/
