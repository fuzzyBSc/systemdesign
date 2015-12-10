systemdesign
============
A simple modelling tool for INCOSE-style systems engineering.
That is, a tool for breaking down units of work across multiple teams
within a large engineering organisation rather than being a network
engineering tool.

This tool is intended to demonstrate basic systems engineering techniques
suitable for orchestrating the design of large systems involving multiple
teams.

Key concepts include:
- Physical breakdown of a system onto Items and Interfaces
- Logical breakdown of a system onto Functions and Flows
- Maintaining consistency of the model between different levels
- Requirements allocation (TODO)
- Hazard Analysis (TODO)

It is intended to work in coordination with a configuration management system
and saves its data as flat CSV files that are amenable to comparison between
versions ("diff") and combining different branches ("merge").

This is primarily intended as a teaching aid for now but should be usable
in real systems development also. The on-disk format is not documented
at this time but is simple and regular enough for other tools to integrate
with it if needed.

Building from sources:
- mvn clean install

Debian package:
- Install the package using your preferred tool
- Either
-- Run from startup menu, or
-- java -jar /usr/share/java/SystemDesign.jar

Generic binary distribution:
- Unzip to your preferred location
- java -jar SytemDesign\*.jar

Version Control:
- Version control is supported in conjunction with "git". Additional version
  control tools may be added in the future.
- A full set of version control operations is not supported. I recommend
  installing rabbitvcs for Linux or tortoiseGit for Windows users. I do
  consider version control integration important for this tool but I don't
  consider it a core competency. Therefore the intended design is to provide
  simple hooks into version control for common operations but to launch the
  main version control system user interface for sophisticated operations.
- A three-way merge mechanism is provided that integrates with the git merge
  command. This mechanism is automatically registered when the tool opens
  a model in a given repository. To revert this change leave the 
  [merge "mergecsvwithuuid"] section in .git/config in place but revert the
  association of \*.csv with this merge in .git/info/attributes So long as
  the .git/config stanza remains in place the automatic configuration will
  not be reattempted.
- Configuration Management is the process of reviewing and approving changes
  controlled within a version control system. Support for comparing with
  past versions and with alternative branches is included in the system.
  No specific workflow for approvals or for maintaining the current
  configuaration register is presently imposed. If you are performing full
  configuration management in conjunction with the tool and think there are
  simple things the tool can do to simplify this task please let me know.

Legal:
- The software itself follows the UNLICENCE (public domain)
- The software relies on dependencies that each have their own licence.
  If in any doubt please consult the legal documentation for each
  dependency. Dependencies are listed in the pom.xml file in the source
  tree.
- The generic binary distribution includes depedancy binaries drawn from
  maven central. The debian package is built for Ubuntu 15.04 and does not
  include dependencies.

Development status: Very early

