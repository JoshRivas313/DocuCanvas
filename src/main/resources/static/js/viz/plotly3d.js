// Renderizado del mapa 3D de embeddings con Plotly.
// Módulo aislado: recibe el contenedor, los datos y callbacks de interacción.

/* global Plotly */

const EMPTY_LAYOUT = {
    paper_bgcolor: 'rgba(0,0,0,0)',
    scene: { xaxis: { visible: false }, yaxis: { visible: false }, zaxis: { visible: false } },
    annotations: [{ text: 'Sin datos para este filtro', showarrow: false, font: { color: '#475569', size: 16 } }],
};

function axis(title) {
    return {
        showgrid: true,
        gridcolor: 'rgba(255,255,255,0.05)',
        zeroline: true,
        zerolinecolor: 'rgba(56, 189, 248, 0.2)',
        showbackground: true,
        backgroundcolor: 'rgba(15, 23, 42, 0.5)',
        title: { text: title, font: { size: 10, color: '#64748b' } },
        showticklabels: false,
    };
}

export function renderMap(plotDiv, chunks, { onHover, onUnhover, onClick }) {
    if (!plotDiv) return;

    if (!chunks || chunks.length === 0) {
        Plotly.newPlot(plotDiv, [], EMPTY_LAYOUT);
        return;
    }

    const clusters = {};
    chunks.forEach((c) => {
        (clusters[c.clusterName] ??= []).push(c);
    });

    const traces = Object.keys(clusters).map((name) => {
        const pts = clusters[name];
        return {
            name,
            x: pts.map((p) => p.coordinates[0]),
            y: pts.map((p) => p.coordinates[1]),
            z: pts.map((p) => p.coordinates[2]),
            customData: pts,
            type: 'scatter3d',
            mode: 'markers',
            marker: {
                size: 6,
                color: pts[0].clusterColor,
                opacity: 0.8,
                line: { color: 'rgba(255,255,255,0.05)', width: 0.5 },
            },
            hoverinfo: 'none',
        };
    });

    const layout = {
        margin: { l: 0, r: 0, b: 0, t: 0 },
        paper_bgcolor: 'rgba(0,0,0,0)',
        showlegend: false,
        scene: {
            xaxis: axis('Dim 1'),
            yaxis: axis('Dim 2'),
            zaxis: axis('Dim 3'),
            bgcolor: '#020617',
            camera: { eye: { x: 1.5, y: 1.5, z: 1.5 } },
        },
    };

    Plotly.newPlot(plotDiv, traces, layout, { responsive: true, displayModeBar: false });

    plotDiv.on('plotly_hover', (data) => {
        onHover(data.points[0].fullData.customData[data.points[0].pointNumber]);
    });
    plotDiv.on('plotly_unhover', () => onUnhover());
    plotDiv.on('plotly_click', (data) => {
        onClick(data.points[0].fullData.customData[data.points[0].pointNumber]);
    });
}

export function focusChunk(plotDiv, coordinates) {
    if (!plotDiv || !coordinates) return;
    const [x, y, z] = coordinates;
    Plotly.relayout(plotDiv, {
        'scene.camera.center': { x, y, z },
        'scene.camera.eye': { x: x + 1, y: y + 1, z: z + 1 },
    });
}
