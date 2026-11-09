package org.openbenches.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openbenches.ui.theme.OpenBenchesHeaderBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onBack: () -> Unit,
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean,
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 0.dp),
            ) {
                OpenBenchesHeaderBanner(
                    globalBenchCount = globalBenchCount,
                    globalBenchCountFailed = globalBenchCountFailed,
                    centerCountOnly = true,
                    authToken = authToken,
                    authProvider = authProvider,
                    authPictureUrl = authPictureUrl,
                    authUsername = authUsername,
                    onClick = onBack,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Privacy & Terms of Service",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Updated 2026-04-13",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TermsSectionHeading("Open Data")
            TermsBody(
                "When you submit a bench to the site (thanks, BTW!) you agree to publish the photos " +
                    "as Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0).",
            )
            TermsBody(
                "The open data set that we publish will contain the photos, inscription, location, " +
                    "and other data and metadata you have uploaded.",
            )
            TermsBody(
                "If you choose to sign in with a social media account, the open data set will contain " +
                    "your chosen social media name and its ID number with the relevant social media service.",
            )

            TermsSectionHeading("Data Collected")
            TermsBody(
                "In order to detect automated abuse, and to ensure that the service can operate efficiently, " +
                    "we record the following information:",
            )
            TermsBulletList(
                listOf(
                    "The IP address used to upload the image.",
                ),
            )
            TermsBody(
                "In order to display images on the site, we record the following information:",
            )
            TermsBulletList(
                listOf(
                    "The date and time of your submission.",
                    "All the EXIF metadata in your photo, including the location where the photo was taken. " +
                        "It may also include details about your camera. This data will be available to anyone who views the photos.",
                    "If you choose to sign in with a social networking service, we record your username, display name, " +
                        "and the internal ID provided by the service. You do not have to use these third party services to upload bench photos. You may do so anonymously.",
                ),
            )
            TermsBody(
                "That's it! We don't place any cookies in your browser, or track you around the web. " +
                    "We don't have advertising, we don't know who you are – and that's the way we like it!",
            )
            TermsBody(
                "You do not have an account with us. We do not store any of your personal data and, as such, " +
                    "have nothing of yours to delete. If you wish to delete your Twitter, GitHub, LinkedIn, WordPress, " +
                    "Facebook, Discord, or Fediverse accounts – you will need to contact those services directly. " +
                    "Deleting your account with a third-party service will not remove the photos and inscriptions you have uploaded here.",
            )

            TermsSectionHeading("Comments")
            TermsBody(
                "If you choose to leave a comment, we will record the name you enter (which does not have to be your real name) " +
                    "and your IP address. You may optionally choose to add your homepage's web address.",
            )
            TermsBody(
                "We manually moderate all comments and may choose to publish, edit, or delete your comment. " +
                    "If your comment is published, it will include the name you entered and the website address (if any).",
            )
            TermsBody(
                "Because we don't ask for identifying information, we cannot associate a comment with a data subject.",
            )

            TermsSectionHeading("3rd Party Services")
            TermsBody("If you choose to sign in, we use Auth0 – read their privacy policy.")
            TermsBody(
                "If you sign in with Twitter, all we know is your username. We can't see your email address. " +
                    "We don't know your password. Update July 2023 – Twitter have changed their API settings and now we have to ask for read and write permissions. " +
                    "We do not want to do this, but we have no choice. Because we use Auth0 for authentication, we do not ever see the API token which is generated – " +
                    "so we cannot use the write permission. If you are worried about this, we strongly suggest you use one of the other login services or post anonymously.",
            )
            TermsBody(
                "If you sign in with GitHub, all we know is your username. We cannot edit your code. " +
                    "We can't alter your profile. We can't see your email address. We don't know your password.",
            )
            TermsBody(
                "If you sign in with Facebook, all we know is your username. We cannot post to your profile. " +
                    "We can't mine your data. We can't see your email address. We don't know your password.",
            )
            TermsBody(
                "If you sign in with LinkedIn, all we know is your username. We cannot post to your profile. " +
                    "We can't mine your data. We can't see your email address. We don't know your password.",
            )
            TermsBody(
                "If you sign in with WordPress.com, all we know is your username. We cannot post as you. " +
                    "We can't mine your data. We can't see your email address. We don't know your password.",
            )
            TermsBody(
                "If you sign in with Discord, all we know is your username. We cannot post as you. " +
                    "We can't mine your data. We can't see your email address. We don't know your password.",
            )
            TermsBody(
                "If you sign in with OpenStreetMap, all we know is your username. We cannot edit your maps. " +
                    "We can't mine your data. We can't see your email address. We don't know your password.",
            )
            TermsBody("For the automatic text detection, your browser will send your image to Google's Cloud Vision Service.")
            TermsBody("Our maps are drawn using OpenFreeMap and ESRI. Each has their own privacy policy.")
            TermsBody("Image proxy service uses weserv.nl.")

            TermsSectionHeading("Other people's privacy")
            TermsBulletList(
                listOf(
                    "Please don't upload identifiable photos of living people without their permission.",
                    "Avoid uploading photos of car licence plates if possible.",
                    "Be respectful – these benches are memorials to loved ones.",
                ),
            )

            TermsSectionHeading("Data Deletion")
            TermsBody(
                "Facebook requires us to provide you with the following information as part of the Facebook Platform terms.",
            )
            TermsBulletList(
                listOf(
                    "A method for users to request the deletion of their Personal Data: If you believe we have any of your personal data, you may request deletion by opening an issue on our GitHub repository and specifying what data of yours you want deleted. We will take steps to verify that you are the owner of such data in order to prevent impersonation.",
                    "A clear statement that indicates users' Personal Data will be deleted upon request: Upon validating your request, we will delete all personal data about you that we hold. Note – this is an Open Data project. Other people and organisations may have taken a copy of the OpenBenches dataset. As per the terms of the Creative Commons Licence, other parties can copy and reuse this data set. While we are happy to delete any personal data you believe we hold on you, you will need to contact other holders of the dataset in order to get them to delete your personal data.",
                ),
            )

            TermsSectionHeading("Complaints, Moderation, and Removals")
            TermsBody(
                "If we decide that your uploaded content is harmful, illegal, or otherwise unsuitable for OpenBenches, we will permanently delete that content.",
            )
            TermsBody(
                "In order to comply with the UK's Online Safety Act, every page which contains UGC (User Generated Content) has a link to a \"Complaints\" form. " +
                    "Users may submit complaints about content which they believe to be harmful, illegal, or otherwise unsuitable for OpenBenches. " +
                    "If we agree with the complaint, we will permanently delete that content.",
            )
            TermsBody("Manifestly unfounded complaints will be ignored.")
            TermsBody(
                "If we determine that your account is abusive, or belongs to a proscribed organisation, or otherwise unsuitable, " +
                    "we may permanently ban your account / IP address from adding or editing UGC.",
            )
            TermsBody("You will not be proactively notified if your content is removed or if your account is banned.")
            TermsBody(
                "If you have a complaint as per Online Harms Act – Section 72 (8) you may submit a complaint using the same form.",
            )
            TermsBody(
                "Note: These moderation actions are at our sole discretion. While we will consider your complaint seriously, " +
                    "we cannot enter into any correspondence about moderation decisions.",
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TermsSectionHeading(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TermsBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TermsBulletList(items: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
