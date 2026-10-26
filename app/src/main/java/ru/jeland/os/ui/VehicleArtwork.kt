package ru.jeland.os.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import ru.jeland.os.R

/** Keep the generated PNG unchanged; rotate it at render time into the shared stage. */
@Composable internal fun VehicleArtwork(modifier:Modifier=Modifier) {
    Layout(modifier=modifier,content={
        Image(painterResource(R.drawable.vehicle_top),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
    }) {measurables,constraints->
        val width=constraints.maxWidth;val height=constraints.maxHeight
        val image=measurables.single().measure(Constraints.fixed(height,width))
        layout(width,height) {
            image.placeWithLayer(0,height){rotationZ=-90f;transformOrigin=TransformOrigin(0f,0f)}
        }
    }
}
