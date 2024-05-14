package org.session.libsession.utilities

enum class Device(val value: String, val service: String = value, val pushAvailable: Boolean = true) {
    ANDROID("android", "firebase"),
    FDROID("fdroid", pushAvailable = false),
    HUAWEI("huawei");
}
