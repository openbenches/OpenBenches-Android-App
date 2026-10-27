package org.openbenches.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import org.openbenches.R
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.openbenches.ui.theme.OpenBenchesHeaderBanner

private const val OPENBENCHES_SPREADSHOP_URL = "https://openbenches.myspreadshop.co.uk/all"
private const val KRYSTAL_AFFILIATE_URL =
    "https://www.awin1.com/cread.php?s=3990878&v=117205&q=524267&r=1062999"
private const val DECODED_LEGAL_URL = "https://decoded.legal/"
private const val RICHARD_LEYTON_URL = "https://leyton.org/"
private const val BEN_SMITH_URL = "https://bensmith.uk/"
private const val GITHUB_SPONSORS_URL = "https://github.com/sponsors/openbenches"
private const val OPEN_COLLECTIVE_URL = "https://opencollective.com/openbenches"

private data class SupportBulletItem(
    val label: String,
    val url: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportOpenBenchesScreen(
    onBack: () -> Unit,
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean,
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
                text = "How to support OpenBenches",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            SupportBody(
                "We want to keep this website free for everyone – and free from advertising.",
            )
            SupportBody(
                "If you'd like to support us, there are three great ways to get involved:",
            )

            SupportSectionHeading("Join Krystal")
            SupportKrystalHostedLine()
            SupportBody("Use code EDENT for a discount on web hosting.")
            SupportBody("Planet-friendly web-hosting from £7pcm.")

            SupportSectionHeading("Swag!")
            SupportBody(
                "You can buy a t-shirt, hat, mug, thermos, stickers, or other merchandise with our logo on it. " +
                    "Show off your gear while you're out benching! Tap the image below to open the shop.",
            )
            SupportBody(
                "Shop: openbenches.myspreadshop.co.uk",
            )
            Image(
                painter = painterResource(R.drawable.openbenches_swag),
                contentDescription = "OpenBenches merchandise – t-shirts, mugs, and caps",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(OPENBENCHES_SPREADSHOP_URL)),
                        )
                    },
                contentScale = ContentScale.FillWidth,
            )

            SupportSectionHeading("Cash!")
            SupportBody(
                "You can sponsor development of this site with a one-off or recurring payment.",
            )
            SupportDonationsLine()

            SupportSectionHeading("Thanks!")
            SupportBody("We'd like to thank the following sponsors for their generosity:")
            SupportBulletList(
                listOf(
                    SupportBulletItem("decoded.legal", DECODED_LEGAL_URL),
                    SupportBulletItem("Richard Leyton", RICHARD_LEYTON_URL),
                    SupportBulletItem("Ben Smith", BEN_SMITH_URL),
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SupportSectionHeading(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SupportKrystalHostedLine() {
    val context = LocalContext.current
    val hostedText = buildAnnotatedString {
        append("OpenBenches is hosted on ")
        pushStringAnnotation(tag = "krystal", annotation = KRYSTAL_AFFILIATE_URL)
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append("Krystal.uk")
        }
        pop()
        append(".")
    }
    ClickableText(
        text = hostedText,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            hostedText.getStringAnnotations(tag = "krystal", start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)),
                    )
                }
        },
    )
}

@Composable
private fun SupportDonationsLine() {
    val context = LocalContext.current
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val donationsText = buildAnnotatedString {
        append("We accept donations via ")
        pushStringAnnotation(tag = "github", annotation = GITHUB_SPONSORS_URL)
        withStyle(linkStyle) {
            append("GitHub sponsors")
        }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "opencollective", annotation = OPEN_COLLECTIVE_URL)
        withStyle(linkStyle) {
            append("Open Collective")
        }
        pop()
        append(".")
    }
    ClickableText(
        text = donationsText,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            donationsText.getStringAnnotations(start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)),
                    )
                }
        },
    )
}

@Composable
private fun SupportBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SupportBulletList(items: List<SupportBulletItem>) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            if (item.url != null) {
                val bulletText = buildAnnotatedString {
                    append("• ")
                    pushStringAnnotation(tag = "link", annotation = item.url)
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(item.label)
                    }
                    pop()
                }
                ClickableText(
                    text = bulletText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { offset ->
                        bulletText.getStringAnnotations(tag = "link", start = offset, end = offset)
                            .firstOrNull()
                            ?.let { annotation ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)),
                                )
                            }
                    },
                )
            } else {
                Text(
                    text = "• ${item.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
