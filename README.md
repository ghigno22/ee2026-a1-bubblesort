# Bubble Algorithm Library: execution-time experiment

Reproduction package for the report "Which Bubble Sort Should Bubble Inc. Ship?" (Experiment 1, Experimentation & Evaluation 2026, Alberto Ghignone).

## Contents
- `src/exp01/`: the three algorithms and the `Sorter` interface (unchanged), `InputGenerator.java`, `Benchmark.java`
- `analysis/analyze.py`: descriptive statistics and figures
- `data/results.csv`: raw data, one row per measurement (1440 rows)
- `data/five_number_summary.csv`, `data/summary.csv`: summaries used in the report
- `data/environment.txt`: Java version, OS, processors and JVM options of the run
- `figures/`: figures used in the report

## Environment of the reported run
MacBook Pro, Apple M3 Pro (11-core CPU), 18 GB RAM, macOS Tahoe 26.3, Oracle JDK 25.0.2, Python 3.9.6 (pandas 2.3.3, matplotlib 3.9.4). Power adapter connected, Low Power Mode off, all other applications closed.

## How to reproduce
```bash
javac -d out src/exp01/*.java
caffeinate -i java -Xms1g -Xmx1g -cp out exp01.Benchmark --sizes 100,1000,5000,10000 --reps 30 --warmup 10 --seed 42 --out data/results.csv
python3 -m venv .venv && source .venv/bin/activate && pip install pandas matplotlib
python analysis/analyze.py data/results.csv
```