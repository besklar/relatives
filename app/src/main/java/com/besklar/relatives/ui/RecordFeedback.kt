package com.besklar.relatives.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.besklar.relatives.R
import com.besklar.relatives.data.RefreshResult
import java.text.DateFormat
import java.util.Date

@Composable
fun failureText(failure: RefreshResult?): String = stringResource(when (failure) {
    RefreshResult.NetworkFailure -> R.string.network_failure
    is RefreshResult.HttpFailure -> R.string.service_failure
    RefreshResult.StorageFailure -> R.string.storage_failure
    else -> R.string.records_failure
})

@Composable
fun savedAtText(time: Long): String = stringResource(R.string.saved_at,
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time)))
