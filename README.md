systemdesign
============
A simple systems engineering modelling tool.

This tool is intended to demonstrate basic systems engineering techniques
suitable for orchestrating the design of large involving multiple teams.

Key concepts include:
- Physical breakdown of a system onto Items and Interfaces
- Logical breakdown of a system onto Functions and Flows
- Requirements allocation
- Hazard Analysis

It is intended to work in coordination with a configuration management system
and saves its data as flat CSV files that are amenable to diffs.

This is primarily intended as a teaching aid for now but should be usable
in real systems development also. The on-disk format is not documented
at this time but is simple and regular enough for other tools to integrate
with it if needed.

Build and run with maven.

Development status: Very early

