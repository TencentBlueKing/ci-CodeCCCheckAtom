# Plugin function

Support all CodeCC code inspection tools under Linux, MacOS, and Windows systems, including code defects (bkcheck, etc.), security vulnerabilities (sensitive information, high-risk components, etc.), code specifications (CppLint, CheckStyle, etc.), cyclomatic complexity, repetition rate, etc. .

# Applicable scene

## Linux:
Public build machine (with Docker pre-installed)
Private build machine (docker needs to be installed)
Build a cluster privately (Docker needs to be installed)

## MacOS:
Private build machine (docker needs to be installed)
Build a cluster privately (Docker needs to be installed)

## Windows (win10 and above):
Private build machine (docker needs to be installed)
Build a cluster privately (Docker needs to be installed)

# Use restricted and restricted solutions
Private build machines need to install Docker


# CodeCC Features
CodeCC is Tencent's leading code analysis platform, providing professional code inspection solutions and services to protect product quality.

- At present, more than ten code inspection tools including commercial, open source, and self-developed have been integrated, covering five dimensions of code defects, security vulnerabilities, coding standards, cyclomatic complexity, and code repetition rate;

- Support rule development framework and tool development framework, and can independently integrate the developed rules or tools into the CodeCC platform;

- Self-developed bkcheck defect inspection tool, which can support defect inspection of game C++ background, Unreal client, and Unity client;

- Deeply integrated with the Blue Shield pipeline, through the quality red line service, you can use the inspection results of CodeCC in the pipeline to control the code base MR/PR, transfer test, deployment and other processes, so that the output of each stage of the pipeline can meet the Quality Standard.

# CodeCC can find what code problems

## Find code bugs

Representative tools: ClangWarning, Clang
Representative rules: API usage, illegal memory access, program freezes, resource leaks, null pointers, inefficient performance...

## Security vulnerabilities found

Representative tools: sensitive information, high-risk components
Representative rules: information leakage such as passwords/keys, encryption risks, XSS, CSRF, injection attacks...

## Code specification, check some logic errors

Representative tools: CppLint, CheckStyle, ESLint, StyleCop, Gometalinter, detekt, PHPCS, PyLint, etc.
Representative rules: comments, empty code blocks, exception handling, naming, formatting, style...

## Control complexity
Representative Tool: Cyclomatic Complexity
Representative rules: function cyclomatic complexity>=20

## Detect repetition rate
Rep Tool: Repetition Rate
Representative rule: file code repetition rate>=5%

## Count the number of lines of code
Representative Tool: Code Statistics
Representative rules: Statistical code lines, comment lines, and blank lines of various languages in the code.