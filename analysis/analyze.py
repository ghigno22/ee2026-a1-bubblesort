"""Descriptive statistics and plots for Assignment 1 (The Bubble Algorithm Library).

Usage:  python3 analysis/analyze.py data/results.csv

Produces
  data/summary.csv              one row per condition (algorithm x input type x size)
  data/five_number_summary.csv  min, Q1, median, Q3, max per condition (ms) for Section 3.2
  figures/time_vs_size.png      median time vs array size, one panel per input type (log y-axis)
  figures/boxplots_<N>.png      distribution of the repetitions at the largest size N
  figures/run_order.png         time vs run order (sanity check for time threats / drift)
"""
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

Z_95 = 1.96  # z-table value for a 95% confidence level


def summarize(df: pd.DataFrame) -> pd.DataFrame:
    g = df.groupby(["algorithm", "input_type", "size"])["time_ms"]
    s = g.agg(n="count", mean="mean", median="median", sd="std", min="min", max="max",
              q1=lambda x: x.quantile(0.25), q3=lambda x: x.quantile(0.75)).reset_index()
    s["iqr"] = s["q3"] - s["q1"]
    s["se"] = s["sd"] / s["n"] ** 0.5
    s["ci95_low"] = s["mean"] - Z_95 * s["se"]
    s["ci95_high"] = s["mean"] + Z_95 * s["se"]
    s["cv_percent"] = 100 * s["sd"] / s["mean"]
    return s


def main(path: str) -> None:
    df = pd.read_csv(path)
    df["time_ms"] = df["time_ns"] / 1e6
    root = Path(path).resolve().parent.parent
    (root / "figures").mkdir(exist_ok=True)

    summary = summarize(df)
    summary.round(4).to_csv(root / "data" / "summary.csv", index=False)

    # Five-number summary (report template, Section 3.2), in milliseconds
    five = summary[["algorithm", "input_type", "size", "min", "q1", "median", "q3", "max"]].copy()
    five["algorithm"] = five["algorithm"].str.replace("BubbleSort", "")
    five = five.sort_values(["size", "input_type", "algorithm"])
    five.round(3).to_csv(root / "data" / "five_number_summary.csv", index=False)

    algorithms = sorted(df["algorithm"].unique())
    types = ["SORTED", "NEARLY_SORTED", "RANDOM", "REVERSED"]
    markers = {a: m for a, m in zip(algorithms, ["o", "s", "^"])}

    # 1) Line plot: size is a continuous ratio variable, so lines between points are meaningful.
    fig, axes = plt.subplots(1, 4, figsize=(16, 4), sharey=True)
    for ax, t in zip(axes, types):
        for a in algorithms:
            d = summary[(summary.algorithm == a) & (summary.input_type == t)].sort_values("size")
            ax.errorbar(d["size"], d["median"], yerr=[d["median"] - d["q1"], d["q3"] - d["median"]],
                        marker=markers[a], capsize=3, label=a.replace("BubbleSort", ""))
        ax.set_title(f"Input: {t}")
        ax.set_xlabel("Array size (elements)")
        ax.set_yscale("log")
        ax.grid(True, which="both", alpha=0.3)
    axes[0].set_ylabel("Execution time (ms, log scale)\nmedian, bars = IQR")
    axes[0].legend()
    fig.tight_layout()
    fig.savefig(root / "figures" / "time_vs_size.png", dpi=150)

    # 2) Box plots at the largest size: algorithm is nominal, so no lines between boxes.
    nmax = df["size"].max()
    fig, axes = plt.subplots(1, 4, figsize=(16, 4))
    for ax, t in zip(axes, types):
        d = df[(df["size"] == nmax) & (df.input_type == t)]
        ax.boxplot([d[d.algorithm == a]["time_ms"] for a in algorithms],
                   tick_labels=[a.replace("BubbleSort", "") for a in algorithms])
        ax.set_title(f"{t}, n = {nmax}")
        ax.set_ylabel("Execution time (ms)")
        ax.tick_params(axis="x", labelrotation=15)
        ax.grid(True, axis="y", alpha=0.3)
    fig.tight_layout()
    fig.savefig(root / "figures" / f"boxplots_{nmax}.png", dpi=150)

    # 3) Sanity check: does time drift with run order (machine heating up, background jobs)?
    d = df[(df["size"] == nmax) & (df.input_type == "RANDOM")]
    fig, ax = plt.subplots(figsize=(8, 4))
    for a in algorithms:
        x = d[d.algorithm == a]
        ax.scatter(x["run_order"], x["time_ms"], s=10, marker=markers[a], label=a.replace("BubbleSort", ""))
    ax.set_xlabel("Run order (trial index)")
    ax.set_ylabel("Execution time (ms)")
    ax.set_title(f"RANDOM, n = {nmax}: time vs run order")
    ax.legend()
    fig.tight_layout()
    fig.savefig(root / "figures" / "run_order.png", dpi=150)

    pd.set_option("display.width", 200)
    print(five[five["size"] == nmax].to_string(index=False))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "data/results.csv")