import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import com.example.arwalking.R

@Composable
fun AndroidCompact2(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .requiredWidth(width = 412.dp)
            .requiredHeight(height = 917.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Android Compact - 2",
            modifier = Modifier
                .fillMaxSize())
        Box(
            modifier = Modifier
                .requiredWidth(width = 412.dp)
                .requiredHeight(height = 144.dp)
                .background(brush = Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.76f),
                    1f to Color(0xff837f7f),
                    start = Offset(201.5f, 58.05f),
                    end = Offset(203f, 144f))))
        Property1Variant2(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 8.dp,
                    y = 50.dp))
        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 106.dp,
                    y = 56.dp)
                .requiredWidth(width = 201.dp)
                .requiredHeight(height = 75.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "image 1",
                modifier = Modifier
                    .requiredWidth(width = 201.dp)
                    .requiredHeight(height = 75.dp))
            Text(
                text = "AR",
                color = Color(0xff94ad0b),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 60.dp,
                        y = 6.dp))
        }
        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 43.dp,
                    y = 312.dp)
                .requiredWidth(width = 327.dp)
                .requiredHeight(height = 47.dp)
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(width = 327.dp)
                    .requiredHeight(height = 47.dp)
                    .clip(shape = RoundedCornerShape(16.dp))
                    .background(color = Color.White.copy(alpha = 0.07f))
                    .shadow(elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp)))
            Text(
                text = "Start suchen...",
                color = Color(0xffe4e0e0),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light),
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 48.dp,
                        y = 11.dp))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 14.dp,
                        y = 11.dp)
                    .requiredSize(size = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(size = 24.dp)
                        .clip(shape = CircleShape)
                        .background(color = Color(0xff0278b6).copy(alpha = 0.14f)))
                Box(
                    modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 5.33.dp,
                            y = 5.33.dp)
                        .requiredSize(size = 13.dp)
                        .clip(shape = CircleShape)
                        .background(color = Color(0xff0278b6))
                        .border(border = BorderStroke(0.5.dp, Color.White),
                            shape = CircleShape))
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.chevrondown1),
            contentDescription = "chevron-down 1",
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 331.dp,
                    y = 324.dp))
        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 203.dp,
                    y = 368.dp)
                .requiredWidth(width = 6.dp)
                .requiredHeight(height = 141.dp)
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 9.01.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 18.03.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 27.04.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 36.05.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 45.07.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 54.08.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 63.1.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 72.11.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 81.12.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 90.14.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 99.15.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 108.16.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 117.18.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 126.19.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 0.dp,
                        y = 135.21.dp)
                    .requiredWidth(width = 6.dp)
                    .requiredHeight(height = 6.dp)
                    .clip(shape = CircleShape)
                    .background(color = Color.White))
        }
        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 43.dp,
                    y = 520.dp)
                .requiredWidth(width = 327.dp)
                .requiredHeight(height = 47.dp)
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(width = 327.dp)
                    .requiredHeight(height = 47.dp)
                    .clip(shape = RoundedCornerShape(16.dp))
                    .background(color = Color.White.copy(alpha = 0.07f))
                    .shadow(elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp)))
            Text(
                text = "Ziel suchen...",
                color = Color(0xffe4e0e0),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light),
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 48.dp,
                        y = 11.dp))
            Image(
                painter = painterResource(id = R.drawable.mappin1),
                contentDescription = "map-pin 1",
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 13.dp,
                        y = 11.dp)
                    .requiredSize(size = 24.dp))
        }
        Icon(
            painter = painterResource(id = R.drawable.chevrondown2),
            contentDescription = "chevron-down 2",
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 325.dp,
                    y = 532.dp))
        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 136.dp,
                    y = 627.dp)
                .requiredWidth(width = 141.dp)
                .requiredHeight(height = 39.dp)
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(width = 141.dp)
                    .requiredHeight(height = 39.dp)
                    .clip(shape = RoundedCornerShape(16.dp))
                    .background(color = Color(0xff94ad0c))
                    .border(border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(16.dp))
                    .shadow(elevation = 4.dp,
                        shape = RoundedCornerShape(16.dp)))
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 14.dp,
                        y = 6.dp)
                    .requiredWidth(width = 107.dp)
                    .requiredHeight(height = 25.dp)
            ) {
                Text(
                    text = "Starten",
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light),
                    modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 35.dp,
                            y = 0.dp))
                Image(
                    painter = painterResource(id = R.drawable.navigation21),
                    contentDescription = "navigation-2 1",
                    modifier = Modifier
                        .requiredSize(size = 24.dp))
            }
        }
    }
}

@Composable
fun Property1Variant2(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .requiredWidth(width = 290.dp)
            .requiredHeight(height = 248.dp)
    ) {
        Property1Default()
    }
}

@Composable
fun Property1Default(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp,
                end = 244.dp,
                top = 18.dp,
                bottom = 196.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape = RoundedCornerShape(7.dp))
                .background(color = Color(0xff94ac0b)))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape = RoundedCornerShape(7.dp))
                .background(color = Color(0xff94ac0b)))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape = RoundedCornerShape(7.dp))
                .background(color = Color(0xff94ac0b)))
    }
}

@Preview(widthDp = 412, heightDp = 917)
@Composable
private fun AndroidCompact2Preview() {
    AndroidCompact2(Modifier)
}