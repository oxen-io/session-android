package org.session.libsession.messaging.sending_receiving.reactions

import org.session.libsession.utilities.Address

class ReactionModel(val id: Long,
                    val author: Address,
                    val emoji: String,
                    val react: Boolean)
