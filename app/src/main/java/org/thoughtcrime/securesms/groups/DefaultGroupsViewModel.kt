package org.thoughtcrime.securesms.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.session.libsession.messaging.open_groups.OpenGroupApiV4
import org.thoughtcrime.securesms.util.State

typealias DefaultGroups = List<OpenGroupApiV4.DefaultGroup>
typealias GroupState = State<DefaultGroups>

class DefaultGroupsViewModel : ViewModel() {

    init {
        OpenGroupApiV4.getDefaultRoomsIfNeeded()
    }

    val defaultRooms = OpenGroupApiV4.defaultRooms.map<DefaultGroups, GroupState> {
        State.Success(it)
    }.onStart {
        emit(State.Loading)
    }.asLiveData()
}