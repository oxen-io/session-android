package org.thoughtcrime.securesms.groups.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.BuildConfig
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.home.search.getSearchName
import org.thoughtcrime.securesms.ui.Avatar
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.LocalPreviewMode
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider


@Composable
fun EmptyPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(id = R.string.activity_create_closed_group_empty_placeholer),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
fun GroupMinimumVersionBanner(modifier: Modifier = Modifier) {
    // Minimum version banner
    val context = LocalContext.current
    val text = remember {
        Phrase.from(context, R.string.groupInviteVersionBanner)
            .put("version", BuildConfig.MINIMUM_GROUP_VERSION)
            .format()
            .toString()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalExtraColors.current.warning)
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 11.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
}

fun LazyListScope.multiSelectMemberList(
    contacts: List<Contact>,
    modifier: Modifier = Modifier,
    selectedContacts: Set<Contact> = emptySet(),
    onListUpdated: (Set<Contact>)->Unit = {},
) {
    items(contacts) { contact ->
        val isSelected = selectedContacts.contains(contact)

        val update = {
            val newList =
                if (isSelected) selectedContacts - contact
                else selectedContacts + contact
            onListUpdated(newList)
        }

        Row(modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = update)
            .padding(vertical = 8.dp, horizontal = 24.dp),
            verticalAlignment = CenterVertically
        ) {
            ContactPhoto(
                contact.sessionID,
                modifier = Modifier
                    .size(48.dp)
            )
            MemberName(name = contact.getSearchName(), modifier = Modifier.padding(16.dp))
            RadioButton(selected = isSelected, onClick = update)
        }
    }
}

val MemberNameStyle = TextStyle(fontWeight = FontWeight.Bold)

@Composable
fun RowScope.MemberName(
    name: String,
    modifier: Modifier = Modifier
) = Text(
    text = name,
    style = MemberNameStyle,
    modifier = modifier
        .weight(1f)
        .align(CenterVertically)
)

fun LazyListScope.deleteMemberList(
    contacts: List<Contact>,
    modifier: Modifier = Modifier,
    onDelete: (Contact) -> Unit,
) {
    item {
        Text(
            text = stringResource(id = R.string.conversation_settings_group_members),
            style = MaterialTheme.typography.subtitle2,
            modifier = modifier
                .padding(vertical = 8.dp)
        )
    }
    if (contacts.isEmpty()) {
        item {
            EmptyPlaceholder(modifier.fillMaxWidth())
        }
    } else {
        items(contacts) { contact ->
            Row(modifier.fillMaxWidth()) {
                ContactPhoto(
                    contact.sessionID,
                    modifier = Modifier
                        .size(48.dp)
                        .align(CenterVertically)
                )
                MemberName(name = contact.getSearchName(), modifier = Modifier.padding(16.dp))
                Image(
                    painterResource(id = R.drawable.ic_baseline_close_24),
                    null,
                    modifier = Modifier
                        .size(32.dp)
                        .align(CenterVertically)
                        .clickable {
                            onDelete(contact)
                        },
                )
            }
        }
    }
}


@Composable
fun RowScope.ContactPhoto(sessionId: String, modifier: Modifier = Modifier) {
    return if (LocalPreviewMode.current) {
        Image(
            painterResource(id = R.drawable.ic_profile_default),
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary),
            contentScale = ContentScale.Inside,
            contentDescription = null,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colors.onPrimary, CircleShape)
        )
    } else {
        val context = LocalContext.current
        // Ideally we migrate to something that doesn't require recipient, or get contact photo another way
        val recipient = remember(sessionId) {
            Recipient.from(context, Address.fromSerialized(sessionId), false)
        }
        Avatar(recipient)
    }
}


@Preview
@Composable
fun PreviewMemberList(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    val random = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
    val previewMembers = setOf(
        Contact(random).apply {
            name = "Person"
        }
    )
    PreviewTheme(themeResId = themeResId) {
        LazyColumn {
            multiSelectMemberList(
                contacts = previewMembers.toList(),
            )
        }
    }
}