package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Destination
import com.example.ui.navigation.NavigationViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NavigationViewModel,
    onNavigateToRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val curatedDestinations by viewModel.curatedDestinations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedDestination by viewModel.selectedDestination.collectAsState()
    val recentDestinations by viewModel.recentDestinations.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()

    var showDetailSheet by remember { mutableStateOf(false) }

    val categories = listOf("All", "Culture", "Heritage", "Food", "Nature")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SpaceNavyDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceNavyDark)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header with App Title & Developer Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Geospatial AR",
                            color = CyanGlow,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Augmented Reality Walking Navigation",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }

                    FilterChip(
                        selected = isDeveloperMode,
                        onClick = { viewModel.toggleDeveloperMode() },
                        label = {
                            Text(
                                text = if (isDeveloperMode) "Walking Sim ON" else "Walking Sim OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = "Sim Mode",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimaryDark,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = CyanGlow
                        ),
                        modifier = Modifier.testTag("home_dev_mode_chip")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("destination_search_input"),
                    placeholder = { Text("Search places, satras, heritage sites...", color = TextMutedDark) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = CyanGlow)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondaryDark)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SpaceNavySurface,
                        unfocusedContainerColor = SpaceNavySurface,
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = SpaceNavyBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { viewModel.setCategoryFilter(category) },
                            label = { Text(category, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SpaceNavySurface,
                                labelColor = TextSecondaryDark,
                                selectedContainerColor = CyanPrimary,
                                selectedLabelColor = SpaceNavyDark
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == category,
                                borderColor = SpaceNavyBorder,
                                selectedBorderColor = CyanGlow
                            ),
                            modifier = Modifier.testTag("category_chip_$category")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Recents section
            if (recentDestinations.isNotEmpty() && searchQuery.isEmpty()) {
                item {
                    Text(
                        text = "Recent Searches",
                        color = TextSecondaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentDestinations) { recent ->
                            RecentDestinationCard(
                                destination = recent,
                                onClick = {
                                    viewModel.selectDestination(recent)
                                    showDetailSheet = true
                                }
                            )
                        }
                    }
                }
            }

            // Section Header
            item {
                Text(
                    text = if (searchQuery.isNotEmpty()) "Search Results" else "Explore Destinations",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Curated Destinations list
            items(curatedDestinations) { dest ->
                DestinationCard(
                    destination = dest,
                    onCardClick = {
                        viewModel.selectDestination(dest)
                        showDetailSheet = true
                    },
                    onQuickStart = {
                        viewModel.selectDestination(dest)
                        viewModel.startNavigation()
                        onNavigateToRoute()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Destination Details Bottom Sheet
        if (showDetailSheet && selectedDestination != null) {
            ModalBottomSheet(
                onDismissRequest = { showDetailSheet = false },
                containerColor = SpaceNavySurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = SpaceNavyBorder) }
            ) {
                DestinationDetailContent(
                    destination = selectedDestination!!,
                    onStartNavigation = {
                        showDetailSheet = false
                        viewModel.startNavigation()
                        onNavigateToRoute()
                    },
                    onClose = { showDetailSheet = false }
                )
            }
        }
    }
}

@Composable
fun DestinationCard(
    destination: Destination,
    onCardClick: () -> Unit,
    onQuickStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("destination_card_${destination.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Category pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (destination.category) {
                            "Culture" -> CyanPrimaryDark.copy(alpha = 0.3f)
                            "Heritage" -> AmberBeacon.copy(alpha = 0.25f)
                            "Nature" -> GreenConfidence.copy(alpha = 0.25f)
                            else -> TealAccent.copy(alpha = 0.25f)
                        }
                    ) {
                        Text(
                            text = destination.category.uppercase(),
                            color = when (destination.category) {
                                "Culture" -> CyanGlow
                                "Heritage" -> AmberBeacon
                                "Nature" -> GreenConfidence
                                else -> TealAccent
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = destination.name,
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = destination.address,
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // Quick Navigate FAB
                IconButton(
                    onClick = onQuickStart,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary)
                        .testTag("quick_navigate_btn_${destination.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Navigate to ${destination.name}",
                        tint = SpaceNavyDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (destination.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = destination.description,
                    color = TextMutedDark,
                    fontSize = 12.sp,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun RecentDestinationCard(
    destination: Destination,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("recent_card_${destination.id}"),
        color = SpaceNavySurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.History, contentDescription = "Recent", tint = CyanGlow, modifier = Modifier.size(16.dp))
            Text(destination.name, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DestinationDetailContent(
    destination: Destination,
    onStartNavigation: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.name,
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    text = destination.address,
                    color = TextSecondaryDark,
                    fontSize = 13.sp
                )
            }
        }

        if (destination.description.isNotEmpty()) {
            Text(
                text = destination.description,
                color = TextPrimaryDark,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        // Features row (VPS ready, Walking distance)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailPill("Category", destination.category, Icons.Default.Category, modifier = Modifier.weight(1f))
            DetailPill("AR Tracking", "VPS Ready", Icons.Default.ViewInAr, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Start Navigation Button
        Button(
            onClick = onStartNavigation,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_navigation_bottom_sheet_btn"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
        ) {
            Icon(Icons.Default.Navigation, contentDescription = "Start", tint = SpaceNavyDark)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start Walking Navigation",
                color = SpaceNavyDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun DetailPill(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = SpaceNavyDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = CyanGlow, modifier = Modifier.size(18.dp))
            Column {
                Text(label, color = TextMutedDark, fontSize = 10.sp)
                Text(value, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
