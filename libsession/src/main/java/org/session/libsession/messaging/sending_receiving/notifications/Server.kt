package org.session.libsession.messaging.sending_receiving.notifications

enum class Server(val url: String, val publicKey: String) {
    LATEST("https://push.getsession.org", "d7557fe563e2610de876c0ac7341b62f3c82d5eea4b62c702392ea4368f51b3b"),
    LEGACY("https://live.apns.getsession.org", "642a6585919742e5a2d4dc51244964fbcd8bcab2b75612407de58b810740d049")
}
