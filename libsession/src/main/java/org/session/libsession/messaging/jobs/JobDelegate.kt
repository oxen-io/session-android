package org.session.libsession.messaging.jobs

interface JobDelegate {

    fun handleJobSucceeded(job: Job, dispatcherName: String)
    fun handleJobFailed(job: Job, dispatcherName: String, error: Exception)
    fun handleJobFailedPermanently(job: Job, dispatcherName: String, error: Exception)
}