systemdesign
============
A simple modelling tool for INCOSE-style systems engineering.
That is, a tool for breaking down units of work across multiple teams
within a large engineering organisation rather than being a network
engineering tool.

This tool is intended to demonstrate basic systems engineering techniques
suitable for orchestrating the design of large involving multiple teams.

Key concepts include:
- Physical breakdown of a system onto Items and Interfaces
- Logical breakdown of a system onto Functions and Flows
- Requirements allocation
- Hazard Analysis

It is intended to work in coordination with a configuration management system
and saves its data as flat CSV files that are amenable to diffs. The diffs
are somewhat human-readible and may be amenable to being presented directly
to technically savvy configuration control boards, however more work is
needed to derive a straightforward "was/is" change proposal.

This is primarily intended as a teaching aid for now but should be usable
in real systems development also. The on-disk format is not documented
at this time but is simple and regular enough for other tools to integrate
with it if needed.

Build and run with maven.

Development status: Very early

