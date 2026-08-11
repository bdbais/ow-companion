package com.bellizia.owcompanion.ui.about

import androidx.annotation.StringRes
import com.bellizia.owcompanion.R

/**
 * What players have reported, and what has been done about it.
 *
 * Kept in the app rather than only in the repository because the people who find these
 * things are not the people who read commit messages. Someone who reported a wrong number
 * should be able to see whether it was believed, fixed, or judged correct as it stood -
 * without asking.
 *
 * The honest part is the third state. One of these was reported in good faith and the data
 * turned out to be right; saying so plainly is worth more than quietly leaving it open.
 *
 * The full reasoning for each lives in `dataset/reports.md`.
 */
data class Report(
    val number: Int,
    @StringRes val title: Int,
    val status: Status,
    /** The version it went out in, for the ones that are done. */
    val version: String? = null,
) {
    enum class Status { Fixed, Open, AsDesigned }
}

val Reports: List<Report> = listOf(
    Report(1, R.string.report_1, Report.Status.Open),
    Report(2, R.string.report_2, Report.Status.Open),
    Report(3, R.string.report_3, Report.Status.Open),
    Report(4, R.string.report_4, Report.Status.Open),
    Report(5, R.string.report_5, Report.Status.Open),
    Report(6, R.string.report_6, Report.Status.Open),
    Report(7, R.string.report_7, Report.Status.Open),
    Report(8, R.string.report_8, Report.Status.Fixed, "1.7.1"),
    Report(9, R.string.report_9, Report.Status.AsDesigned),
    Report(10, R.string.report_10, Report.Status.Fixed, "1.7.3"),
    Report(11, R.string.report_11, Report.Status.Fixed, "1.7.2"),
    Report(12, R.string.report_12, Report.Status.Open),
)

val fixedCount: Int get() = Reports.count { it.status == Report.Status.Fixed }
