// Store Simulator Frontend Game Engine & API Sync

const API_BASE = 'http://localhost:8080/api/v1';

// Simulation Configuration
const GRID_SIZE = 14;
const TILE_W = 56;
const TILE_H = 28;

// Standing and Obstacle coordinates
const MAP_BLOCKS = {
    shelves: [
        { id: 'Boisson', name: 'Soda Shelf', x: 4, y: 3, standX: 4, standY: 4, color: '#88c0d0' },
        { id: 'Snack', name: 'Chips Shelf', x: 4, y: 10, standX: 4, standY: 9, color: '#ebcb8b' },
        { id: 'Culture', name: 'Book Shelf', x: 9, y: 3, standX: 9, standY: 4, color: '#bf616a' },
        { id: 'High-Tech', name: 'Console Shelf', x: 9, y: 10, standX: 9, standY: 9, color: '#a3be8c' }
    ],
    counter: { x: 12, y: 3, standX: 11, standY: 3 },
    storage: { x: 12, y: 12, standX: 11, standY: 12 },
    door: { x: 0, y: 7 },
    props: [
        { name: 'Plante 1', x: 1, y: 1, type: 'plant' },
        { name: 'Plante 2', x: 1, y: 12, type: 'plant' },
        { name: 'Poubelle', x: 12, y: 1, type: 'trash' }
    ]
};

// Application State
let simulationActive = false;
let products = [];
let localCustomers = [];
let activeDelivery = null; // Current delivery sequence state
let logs = [];
let checkoutQueue = []; // Array of customer IDs waiting at register
let syncInterval = null;
let lastSpawnTime = 0;

// Canvas setup
const canvas = document.getElementById('simulation-canvas');
const ctx = canvas.getContext('2d');

// UI DOM references
const btnStart = document.getElementById('btn-start');
const btnStop = document.getElementById('btn-stop');
const btnReset = document.getElementById('btn-reset');
const btnTick = document.getElementById('btn-tick');
const btnClearLogs = document.getElementById('btn-clear-logs');
const statusText = document.getElementById('status-text');
const consoleLogs = document.getElementById('console-logs');

const statRevenue = document.getElementById('stat-revenue');
const statCapital = document.getElementById('stat-capital');
const statTransactions = document.getElementById('stat-transactions');
const statRestocks = document.getElementById('stat-restocks');
const inventoryList = document.getElementById('product-inventory-list');

// Setup Canvas size
function resizeCanvas() {
    const rect = canvas.parentElement.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = rect.height;
}
window.addEventListener('resize', resizeCanvas);
resizeCanvas();

// Static Cashier entity
const cashier = {
    name: 'Caissier',
    color: '#a3be8c', // Green uniform shirt
    x: 13,
    y: 3,
    budget: 0,
    carrying: false,
    emote: null,
    hairColor: '#4c566a' // Grey hair
};

// Camera State (Panning & Zooming)
const camera = {
    x: 0,
    y: 0,
    zoom: 1.0,
    isDragging: false,
    dragStartX: 0,
    dragStartY: 0
};

// Mouse Drag / Panning Listeners
canvas.addEventListener('mousedown', (e) => {
    camera.isDragging = true;
    camera.dragStartX = e.clientX - camera.x;
    camera.dragStartY = e.clientY - camera.y;
    canvas.style.cursor = 'grabbing';
});

canvas.addEventListener('mousemove', (e) => {
    if (camera.isDragging) {
        camera.x = e.clientX - camera.dragStartX;
        camera.y = e.clientY - camera.dragStartY;
    }
});

window.addEventListener('mouseup', () => {
    camera.isDragging = false;
    canvas.style.cursor = 'grab';
});

canvas.style.cursor = 'grab';

// Mouse Wheel Zoom Listener
canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    const zoomFactor = 1.1;
    let newZoom = camera.zoom;
    
    if (e.deltaY < 0) {
        newZoom = Math.min(2.5, camera.zoom * zoomFactor);
    } else {
        newZoom = Math.max(0.5, camera.zoom / zoomFactor);
    }
    
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    const centerX = canvas.width / 2 + camera.x;
    const centerY = canvas.height / 2 - 170 + camera.y;
    
    const dx = mouseX - centerX;
    const dy = mouseY - centerY;
    
    camera.x -= dx * (newZoom / camera.zoom - 1);
    camera.y -= dy * (newZoom / camera.zoom - 1);
    camera.zoom = newZoom;
}, { passive: false });

// --- LOGGER UTILITIES ---
function addLog(message, type = 'system') {
    const timestamp = new Date().toLocaleTimeString();
    const logObj = { timestamp, message, type };
    logs.push(logObj);
    
    const line = document.createElement('div');
    line.className = `log-line ${type}`;
    line.innerHTML = `<span style="color: var(--text-dimmed)">[${timestamp}]</span> ${message}`;
    consoleLogs.appendChild(line);
    
    // Auto scroll to bottom
    consoleLogs.scrollTop = consoleLogs.scrollHeight;
    
    // Limit log display lines
    while (consoleLogs.childNodes.length > 100) {
        consoleLogs.removeChild(consoleLogs.firstChild);
    }
}

// --- PATHFINDING (BFS on Walkable Grid) ---
function isBlocked(x, y) {
    // Check if cell matches a static obstacle
    if (x === MAP_BLOCKS.counter.x && (y === MAP_BLOCKS.counter.y || y === MAP_BLOCKS.counter.y - 1)) return true;
    if (x === MAP_BLOCKS.storage.x && y === MAP_BLOCKS.storage.y) return true;
    for (const shelf of MAP_BLOCKS.shelves) {
        if (x === shelf.x && y === shelf.y) return true;
    }
    for (const prop of MAP_BLOCKS.props) {
        if (x === prop.x && y === prop.y) return true;
    }
    return false;
}

function findPath(startX, startY, targetX, targetY) {
    const queue = [[startX, startY, []]];
    const visited = new Set();
    visited.add(`${startX},${startY}`);
    
    const dirs = [
        [0, 1], [0, -1], [1, 0], [-1, 0]
    ];
    
    while (queue.length > 0) {
        const [cx, cy, path] = queue.shift();
        
        if (cx === targetX && cy === targetY) {
            return path.concat([[cx, cy]]);
        }
        
        for (const [dx, dy] of dirs) {
            const nx = cx + dx;
            const ny = cy + dy;
            
            if (nx >= 0 && nx < GRID_SIZE && ny >= 0 && ny < GRID_SIZE) {
                const coordStr = `${nx},${ny}`;
                // Walkable if not blocked OR if it is the target destination
                const walkable = !isBlocked(nx, ny) || (nx === targetX && ny === targetY);
                if (!visited.has(coordStr) && walkable) {
                    visited.add(coordStr);
                    queue.push([nx, ny, path.concat([[cx, cy]])]);
                }
            }
        }
    }
    return null; // Path not found
}

// --- REST CLIENT SYNCING ---
async function fetchStatus() {
    try {
        const res = await fetch(`${API_BASE}/simulation/status`);
        const data = await res.json();
        
        simulationActive = data.active;
        statRevenue.innerText = `${data.totalRevenue.toFixed(2)} €`;
        statCapital.innerText = `${data.currentCapital.toFixed(2)} €`;
        statTransactions.innerText = data.transactionCount;
        statRestocks.innerText = data.restockCount;
        
        // Update button states based on active status
        if (simulationActive) {
            btnStart.disabled = true;
            btnStop.disabled = false;
            statusText.innerText = "En cours";
            statusText.className = "status-online";
        } else {
            btnStart.disabled = false;
            btnStop.disabled = true;
            statusText.innerText = "Arrêté";
            statusText.className = "status-offline";
        }
        
        // Fetch products
        await fetchProducts();
        
        // Fetch pending restocks
        await fetchRestocks();
        
    } catch (err) {
        console.error('Error fetching status:', err);
    }
}

async function fetchProducts() {
    try {
        const res = await fetch(`${API_BASE}/products`);
        products = await res.json();
        
        // Render inventory list in sidebar
        inventoryList.innerHTML = '';
        products.forEach(p => {
            const container = document.createElement('div');
            container.className = 'inventory-item';
            
            // Calculate percentage
            const maxCapacity = 50; // Reference max capacity
            const pct = Math.min(100, (p.stockQuantity / maxCapacity) * 100);
            
            // Determine color class
            let colorClass = 'stock-normal';
            if (p.stockQuantity <= p.restockThreshold) {
                colorClass = 'stock-critical';
            } else if (p.stockQuantity <= p.restockThreshold * 1.5) {
                colorClass = 'stock-warning';
            }
            
            container.innerHTML = `
                <div class="inventory-item-header">
                    <span class="product-name">${p.name} (${p.category})</span>
                    <span class="product-stock">${p.stockQuantity} u. / ${p.sellingPrice.toFixed(2)}€</span>
                </div>
                <div class="stock-bar-container">
                    <div class="stock-bar ${colorClass}" style="width: ${pct}%"></div>
                </div>
            `;
            inventoryList.appendChild(container);
        });
    } catch (err) {
        console.error('Error fetching products:', err);
    }
}

async function fetchRestocks() {
    try {
        const res = await fetch(`${API_BASE}/products/restock/orders`);
        const orders = await res.json();
        
        // Find pending restocks that aren't visualised yet
        const pending = orders.find(o => o.status === 'En cours');
        if (pending && !activeDelivery) {
            // Trigger delivery sequence
            triggerDeliverySequence(pending);
        }
    } catch (err) {
        console.error('Error fetching restocks:', err);
    }
}

async function startSimulation() {
    try {
        await fetch(`${API_BASE}/simulation/start`, { method: 'POST' });
        addLog('Simulation Démarrée', 'system');
        await fetchStatus();
    } catch (err) {
        addLog('Erreur lors du démarrage', 'error');
    }
}

async function stopSimulation() {
    try {
        await fetch(`${API_BASE}/simulation/stop`, { method: 'POST' });
        addLog('Simulation mise en Pause', 'system');
        await fetchStatus();
    } catch (err) {
        addLog('Erreur lors de la mise en pause', 'error');
    }
}

async function resetSimulation() {
    try {
        await fetch(`${API_BASE}/simulation/reset`, { method: 'POST' });
        addLog('Simulation Réinitialisée', 'system');
        localCustomers = [];
        checkoutQueue = [];
        activeDelivery = null;
        await fetchStatus();
    } catch (err) {
        addLog('Erreur lors de la réinitialisation', 'error');
    }
}

async function tickSimulation() {
    try {
        await fetch(`${API_BASE}/simulation/tick`, { method: 'POST' });
        addLog('Simulation Tick exécuté', 'system');
        // Spawn a PNJ customer manually if tick triggered
        await generatePNJCustomer();
        await fetchStatus();
    } catch (err) {
        addLog('Erreur lors du tick', 'error');
    }
}

async function generatePNJCustomer() {
    try {
        const res = await fetch(`${API_BASE}/customers/generate`, { method: 'POST' });
        if (res.status === 201) {
            const customerData = await res.json();
            
            // Build movement targets based on cart items
            const targets = [];
            customerData.cart.forEach(item => {
                const shelf = MAP_BLOCKS.shelves.find(s => s.id === item.category);
                if (shelf) {
                    targets.push({
                        name: `Rayon ${item.name}`,
                        x: shelf.standX,
                        y: shelf.standY,
                        type: 'shelf',
                        item: item
                    });
                }
            });
            
            // Add checkout target
            targets.push({
                name: 'Caisse',
                x: MAP_BLOCKS.counter.standX,
                y: MAP_BLOCKS.counter.standY,
                type: 'checkout'
            });
            
            // Add exit target
            targets.push({
                name: 'Sortie',
                x: MAP_BLOCKS.door.x,
                y: MAP_BLOCKS.door.y,
                type: 'exit'
            });

            // Create client entity with randomized hair
            const hairColors = ['#5e3e29', '#ebcb8b', '#2e3440', '#bf616a', '#d08770', '#88c0d0'];
            const hairColor = hairColors[Math.floor(Math.random() * hairColors.length)];

            const pnj = {
                id: customerData.id,
                name: customerData.name,
                budget: customerData.budget,
                cart: customerData.cart,
                x: MAP_BLOCKS.door.x,
                y: MAP_BLOCKS.door.y,
                speed: 0.05 + Math.random() * 0.02,
                color: `hsl(${Math.random() * 360}, 65%, 55%)`,
                hairColor: hairColor,
                targets: targets,
                currentTargetIndex: 0,
                path: null,
                pathIndex: 0,
                state: 'ENTERING', // ENTERING, SHOPPING, WAITING_CHECKOUT, CHECKING_OUT, EXITING
                waitTimer: 0,
                emote: null, // Emote type: 'question', 'clock', 'heart', 'angry'
                emoteTimer: 0
            };
            
            localCustomers.push(pnj);
            addLog(`PNJ Client ${pnj.name} est entré dans le magasin avec ${pnj.budget.toFixed(2)}€`, 'spawn');
        }
    } catch (err) {
        console.error('Error generating customer:', err);
    }
}

// --- DELIVERY RESTOCK SEQUENCE ---
function triggerDeliverySequence(order) {
    addLog(`[LIVRAISON] Camion en route avec 50 unités de ${order.product.name}`, 'restock');
    
    // Delivery state
    activeDelivery = {
        orderId: order.id,
        productName: order.product.name,
        category: order.product.category,
        state: 'TRUCK_ARRIVING', // TRUCK_ARRIVING, WORKER_ENTERING, DROPPING_STORAGE, RESTOCKING_SHELF, WORKER_EXITING, COMPLETE
        x: MAP_BLOCKS.door.x,
        y: MAP_BLOCKS.door.y,
        speed: 0.06,
        timer: 120, // Frames timer
        truckAnim: 0, // Truck screen slide progress (0 to 1)
        worker: null
    };
}

// --- RENDERING & ANIMATIONS ---

// Convert isometric grid coordinates to screen coordinates
function isoToScreen(gridX, gridY) {
    const screenX = (gridX - gridY) * (TILE_W / 2);
    const screenY = (gridX + gridY) * (TILE_H / 2);
    return { x: screenX, y: screenY };
}

// Color brightness utility for shading products
function adjustColorBrightness(hex, percent) {
    let num = parseInt(hex.replace("#",""), 16),
    amt = Math.round(2.55 * percent),
    R = (num >> 16) + amt,
    G = (num >> 8 & 0x00FF) + amt,
    B = (num & 0x0000FF) + amt;
    return "#" + (0x1000000 + (R<255?R<0?0:R:255)*0x10000 + (G<255?G<0?0:G:255)*0x100 + (B<255?B<0?0:B:255)).toString(16).slice(1);
}

// Draw a flat isometric tile
function drawTile(x, y, color, borderColor = 'rgba(255,255,255,0.06)') {
    const pt = isoToScreen(x, y);
    
    ctx.beginPath();
    ctx.moveTo(pt.x, pt.y);
    ctx.lineTo(pt.x + TILE_W / 2, pt.y + TILE_H / 2);
    ctx.lineTo(pt.x, pt.y + TILE_H);
    ctx.lineTo(pt.x - TILE_W / 2, pt.y + TILE_H / 2);
    ctx.closePath();
    
    ctx.fillStyle = color;
    ctx.fill();
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 1;
    ctx.stroke();
}

// Draw a welcome mat on the floor at the door
function drawWelcomeMat(x, y) {
    const pt = isoToScreen(x, y);
    
    ctx.beginPath();
    ctx.moveTo(pt.x, pt.y);
    ctx.lineTo(pt.x + TILE_W / 2, pt.y + TILE_H / 2);
    ctx.lineTo(pt.x, pt.y + TILE_H);
    ctx.lineTo(pt.x - TILE_W / 2, pt.y + TILE_H / 2);
    ctx.closePath();
    
    ctx.fillStyle = '#bf616a'; // Nord red mat
    ctx.fill();
    ctx.strokeStyle = '#d08770';
    ctx.lineWidth = 1.5;
    ctx.stroke();
    
    // Draw welcome text
    ctx.fillStyle = '#eceff4';
    ctx.font = 'bold 7px Outfit';
    ctx.textAlign = 'center';
    ctx.fillText('WELCOME', pt.x, pt.y + TILE_H / 2 + 3);
}

// Draw left wall segment
function drawLeftWall(x, y, height = 45) {
    const pt = isoToScreen(x, y);
    
    ctx.beginPath();
    ctx.moveTo(pt.x - TILE_W / 2, pt.y + TILE_H / 2);
    ctx.lineTo(pt.x - TILE_W / 2, pt.y + TILE_H / 2 - height);
    ctx.lineTo(pt.x, pt.y - height);
    ctx.lineTo(pt.x, pt.y);
    ctx.closePath();
    
    ctx.fillStyle = '#3b4252';
    ctx.fill();
    ctx.strokeStyle = '#4c566a';
    ctx.stroke();
    
    // Draw brick lines
    ctx.beginPath();
    ctx.moveTo(pt.x - TILE_W / 2, pt.y + TILE_H / 2 - height/2);
    ctx.lineTo(pt.x, pt.y - height/2);
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.stroke();
}

// Draw right wall segment
function drawRightWall(x, y, height = 45) {
    const pt = isoToScreen(x, y);
    
    ctx.beginPath();
    ctx.moveTo(pt.x, pt.y);
    ctx.lineTo(pt.x, pt.y - height);
    ctx.lineTo(pt.x + TILE_W / 2, pt.y + TILE_H / 2 - height);
    ctx.lineTo(pt.x + TILE_W / 2, pt.y + TILE_H / 2);
    ctx.closePath();
    
    ctx.fillStyle = '#434c5e';
    ctx.fill();
    ctx.strokeStyle = '#4c566a';
    ctx.stroke();
    
    // Draw brick lines
    ctx.beginPath();
    ctx.moveTo(pt.x, pt.y - height/2);
    ctx.lineTo(pt.x + TILE_W / 2, pt.y + TILE_H / 2 - height/2);
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.stroke();
}

// Draw a 3D isometric box
function draw3DBox(x, y, wSize, hSize, baseColor, topColor, rightColor, heightOffset = 0) {
    const pt = isoToScreen(x, y);
    const px = pt.x;
    const py = pt.y + TILE_H/2 - heightOffset;
    
    const h = hSize;
    const halfW = TILE_W / 2 * wSize;
    const halfH = TILE_H / 2 * wSize;
    
    // Left Face
    ctx.beginPath();
    ctx.moveTo(px - halfW, py);
    ctx.lineTo(px - halfW, py - h);
    ctx.lineTo(px, py - halfH - h);
    ctx.lineTo(px, py - halfH);
    ctx.closePath();
    ctx.fillStyle = baseColor;
    ctx.fill();
    
    // Right Face
    ctx.beginPath();
    ctx.moveTo(px, py - halfH);
    ctx.lineTo(px, py - halfH - h);
    ctx.lineTo(px + halfW, py - h);
    ctx.lineTo(px + halfW, py);
    ctx.closePath();
    ctx.fillStyle = rightColor;
    ctx.fill();
    
    // Top Face
    ctx.beginPath();
    ctx.moveTo(px, py - halfH - h);
    ctx.lineTo(px - halfW, py - h);
    ctx.lineTo(px, py + halfH - h);
    ctx.lineTo(px + halfW, py - h);
    ctx.closePath();
    ctx.fillStyle = topColor;
    ctx.fill();
}

// Draw a mini product item with specific visual skins based on its category
function drawMiniProduct(category, gridX, gridY, heightOffset, sOffsetX, sOffsetY) {
    const pt = isoToScreen(gridX, gridY);
    const px = pt.x + sOffsetX;
    const py = pt.y + TILE_H / 2 - heightOffset + sOffsetY;

    if (category === 'Boisson') {
        // Soda cans: mini red cylindrical cans with silver tops and white label stripe
        ctx.fillStyle = '#bf616a'; // Red body
        ctx.fillRect(px - 1.5, py - 5, 3, 5);
        ctx.fillStyle = '#eceff4'; // White label
        ctx.fillRect(px - 1.5, py - 3, 3, 1.5);
        ctx.fillStyle = '#d8dee9'; // Silver top rim
        ctx.fillRect(px - 1.5, py - 6, 3, 1);
    } else if (category === 'Snack') {
        // Chips bag: triangular yellow bag with red seal logo badge
        ctx.fillStyle = '#ebcb8b'; // Yellow bag body
        ctx.beginPath();
        ctx.moveTo(px - 2.5, py);
        ctx.lineTo(px - 3, py - 6);
        ctx.lineTo(px, py - 8); // Crinkled top peak
        ctx.lineTo(px + 3, py - 6);
        ctx.lineTo(px + 2.5, py);
        ctx.closePath();
        ctx.fill();
        // Red badge
        ctx.fillStyle = '#d08770'; 
        ctx.beginPath();
        ctx.arc(px, py - 3.5, 1, 0, Math.PI*2);
        ctx.fill();
    } else if (category === 'Culture') {
        // Books: standing blue hardcover book with white paper pages visible
        ctx.fillStyle = '#81a1c1'; // Blue book spine / cover
        ctx.fillRect(px - 2, py - 7, 2, 7);
        ctx.fillStyle = '#eceff4'; // White page rim
        ctx.fillRect(px, py - 6, 1.5, 6);
        ctx.fillStyle = '#5e81ac'; // Dark blue back cover edge
        ctx.fillRect(px + 1.5, py - 7, 1, 7);
    } else if (category === 'High-Tech') {
        // Game console: grey console body box with blue glowing logo
        ctx.fillStyle = '#4c566a'; // Grey console box
        ctx.fillRect(px - 3.5, py - 3.5, 7, 3.5);
        ctx.fillStyle = '#88c0d0'; // Blue glowing power led logo
        ctx.fillRect(px - 1.5, py - 2.5, 3, 1.5);
    }
}

// Draw a cardboard box with shipping tape details in storage zone
function drawDetailedBox(gridX, gridY, size, h, heightOffset, sOffsetX, sOffsetY) {
    const pt = isoToScreen(gridX, gridY);
    const px = pt.x + sOffsetX;
    const py = pt.y + TILE_H / 2 - heightOffset + sOffsetY;
    
    const halfW = TILE_W / 2 * size;
    const halfH = TILE_H / 2 * size;
    
    const baseColor = '#b6926b'; // Cardboard brown
    const topColor = '#cbb092';
    const rightColor = '#8a653e';
    const tapeColor = '#4c566a'; // Dark grey tape
    
    // Left Face
    ctx.beginPath();
    ctx.moveTo(px - halfW, py);
    ctx.lineTo(px - halfW, py - h);
    ctx.lineTo(px, py - halfH - h);
    ctx.lineTo(px, py - halfH);
    ctx.closePath();
    ctx.fillStyle = baseColor;
    ctx.fill();
    
    // Right Face
    ctx.beginPath();
    ctx.moveTo(px, py - halfH);
    ctx.lineTo(px, py - halfH - h);
    ctx.lineTo(px + halfW, py - h);
    ctx.lineTo(px + halfW, py);
    ctx.closePath();
    ctx.fillStyle = rightColor;
    ctx.fill();
    
    // Top Face
    ctx.beginPath();
    ctx.moveTo(px, py - halfH - h);
    ctx.lineTo(px - halfW, py - h);
    ctx.lineTo(px, py + halfH - h);
    ctx.lineTo(px + halfW, py - h);
    ctx.closePath();
    ctx.fillStyle = topColor;
    ctx.fill();
    
    // Draw top tape line
    ctx.beginPath();
    ctx.moveTo(px - halfW * 0.1, py - h);
    ctx.lineTo(px + halfW * 0.2, py - halfH - h);
    ctx.strokeStyle = tapeColor;
    ctx.lineWidth = 1.5;
    ctx.stroke();
}

// Renders characters (customers & delivery workers) with detailed pixel-art skins and bobbing/legs animations
function drawCharacter(c) {
    const pt = isoToScreen(c.x, c.y);
    const cx = pt.x;
    
    // Bobbing and arm swing animations when walking (path is active)
    let bob = 0;
    let swing = 0;
    if (c.path) {
        bob = Math.abs(Math.sin((c.x + c.y) * Math.PI * 3.5)) * 4;
        swing = Math.sin((c.x + c.y) * Math.PI * 3.5);
    }
    
    const cy = pt.y + TILE_H / 2 - bob;
    
    // Character Shadow (stays flat on the floor tile)
    const shadowY = pt.y + TILE_H / 2;
    ctx.beginPath();
    ctx.ellipse(cx, shadowY, 11, 4.5, 0, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.25)';
    ctx.fill();
    
    // Draw legs & pants (Nord dark pants)
    ctx.fillStyle = '#3b4252'; 
    ctx.fillRect(cx - 3.5, cy - 6, 2, 6);
    ctx.fillRect(cx + 1.5, cy - 6, 2, 6);
    
    // Draw shoes
    ctx.fillStyle = '#2e3440'; 
    ctx.fillRect(cx - 4.5, cy - 1, 2, 1.5);
    ctx.fillRect(cx + 0.5, cy - 1, 2, 1.5);
    
    // Draw Torso (Shirt)
    ctx.fillStyle = c.color || '#d8dee9';
    ctx.fillRect(cx - 5, cy - 18, 10, 12);
    
    // Draw Arms swinging
    ctx.fillStyle = c.color || '#d8dee9';
    const leftArmOffset = swing * 4.5;
    ctx.fillRect(cx - 7, cy - 17 + leftArmOffset, 2, 8);
    ctx.fillRect(cx + 5, cy - 17 - leftArmOffset, 2, 8);
    
    // Hands (skin tone)
    ctx.fillStyle = '#ffdbac';
    ctx.fillRect(cx - 7, cy - 9 + leftArmOffset, 2, 2);
    ctx.fillRect(cx + 5, cy - 9 - leftArmOffset, 2, 2);
    
    // Draw Head
    ctx.fillStyle = '#ffdbac'; // Peach skin tone
    ctx.fillRect(cx - 4, cy - 26, 8, 8);
    
    // Eyes
    ctx.fillStyle = '#2e3440';
    ctx.fillRect(cx - 2, cy - 23, 1, 1.5);
    ctx.fillRect(cx + 1, cy - 23, 1, 1.5);
    
    // Hair styling
    const hair = c.hairColor || '#5e3e29';
    ctx.fillStyle = hair;
    ctx.fillRect(cx - 5, cy - 29, 10, 3.5); // Top
    ctx.fillRect(cx - 5, cy - 26, 1.5, 4);  // Left side
    ctx.fillRect(cx + 3.5, cy - 26, 1.5, 4); // Right side
    
    // Cargo indicator (if carrying box)
    if (c.carrying) {
        ctx.fillStyle = '#b6926b';
        ctx.fillRect(cx - 5, cy - 17, 10, 7);
        ctx.strokeStyle = '#8a653e';
        ctx.lineWidth = 1;
        ctx.strokeRect(cx - 5, cy - 17, 10, 7);
    }
    
    // Name Tag / Emote floating above head
    ctx.font = 'bold 9px Outfit';
    ctx.textAlign = 'center';
    
    // Draw Name Tag
    const tagText = `${c.name} (${c.budget.toFixed(1)}€)`;
    const textWidth = ctx.measureText(tagText).width;
    
    ctx.fillStyle = 'rgba(30, 34, 47, 0.8)';
    ctx.fillRect(cx - textWidth/2 - 4, cy - 43, textWidth + 8, 12);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.strokeRect(cx - textWidth/2 - 4, cy - 43, textWidth + 8, 12);
    
    ctx.fillStyle = '#e5e9f0';
    ctx.fillText(tagText, cx, cy - 34);
    
    // Draw Emote Bubbles
    if (c.emote) {
        ctx.font = '12px Apple Color Emoji, Segoe UI Emoji, sans-serif';
        ctx.fillText(c.emote, cx, cy - 48);
    }
}

// Render the Delivery Truck
function drawDeliveryTruck() {
    if (!activeDelivery) return;
    
    // Slide truck into bottom left parking lot area
    const startX = -120;
    const endX = 80;
    const currentX = startX + (endX - startX) * activeDelivery.truckAnim;
    const currentY = canvas.height - 180;
    
    // Draw simple truck outline
    ctx.save();
    
    // Shadow
    ctx.fillStyle = 'rgba(0,0,0,0.2)';
    ctx.fillRect(currentX - 10, currentY + 35, 140, 15);
    
    // Truck Cabin (Orange)
    ctx.fillStyle = '#bf616a';
    ctx.fillRect(currentX + 90, currentY, 35, 40);
    ctx.fillStyle = '#a3be8c'; // Windshield
    ctx.fillRect(currentX + 110, currentY + 5, 15, 12);
    
    // Truck Container (White/Grey)
    ctx.fillStyle = '#e5e9f0';
    ctx.fillRect(currentX, currentY - 15, 90, 55);
    ctx.strokeStyle = '#d8dee9';
    ctx.strokeRect(currentX, currentY - 15, 90, 55);
    
    // Logo text on truck
    ctx.fillStyle = '#4c566a';
    ctx.font = 'bold 10px Outfit';
    ctx.fillText('DELIVERY', currentX + 45, currentY + 15);
    
    // Wheels
    ctx.fillStyle = '#2e3440';
    ctx.beginPath();
    ctx.arc(currentX + 25, currentY + 40, 10, 0, Math.PI * 2);
    ctx.arc(currentX + 70, currentY + 40, 10, 0, Math.PI * 2);
    ctx.arc(currentX + 105, currentY + 40, 10, 0, Math.PI * 2);
    ctx.fill();
    
    ctx.restore();
}

// --- ENGINE CORE GAME LOOP (60 FPS Rendering) ---
function updateAndRender() {
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Save state and apply camera matrices
    ctx.save();
    ctx.translate(canvas.width / 2 + camera.x, canvas.height / 2 - 170 + camera.y);
    ctx.scale(camera.zoom, camera.zoom);
    
    // Draw floor grid and objects
    // Loop y+x from 0 to 26 (since 13+13 = 26 is the max sum for a 14x14 grid)
    const maxSum = 2 * GRID_SIZE - 2;
    for (let sum = 0; sum <= maxSum; sum++) {
        for (let x = 0; x < GRID_SIZE; x++) {
            const y = sum - x;
            if (y >= 0 && y < GRID_SIZE) {
                // 1. Draw floor tile
                let tileColor = (x + y) % 2 === 0 ? '#2e3440' : '#353c4a';
                
                // Highlight storage zone
                if (x === MAP_BLOCKS.storage.x && y === MAP_BLOCKS.storage.y) {
                    tileColor = '#4c566a';
                }
                
                drawTile(x, y, tileColor);
                
                // Draw welcome mat at the door
                if (x === MAP_BLOCKS.door.x && y === MAP_BLOCKS.door.y) {
                    drawWelcomeMat(x, y);
                }
                
                // 2. Draw outer walls
                if (x === 0) {
                    // Draw a door frame on (0,7)
                    if (y === MAP_BLOCKS.door.y) {
                        drawLeftWall(x, y, 15); // Low door frame wall
                    } else {
                        drawLeftWall(x, y);
                    }
                }
                if (y === 0) {
                    drawRightWall(x, y);
                }
                
                // 3. Draw static furniture / obstacles
                
                // Counter & Cash register
                if (x === MAP_BLOCKS.counter.x && y === MAP_BLOCKS.counter.y) {
                    // Counter desk: slate base, marble white top
                    draw3DBox(x, y, 1.0, 18, '#4c566a', '#e5e9f0', '#3b4252');
                    
                    // Cash Register machine details on top
                    // slanted keyboard slope
                    draw3DBox(x, y, 0.4, 4, '#d8dee9', '#eceff4', '#e5e9f0', 18);
                    
                    // Pole and small green terminal screen
                    const pt = isoToScreen(x, y);
                    const cx = pt.x;
                    const cy = pt.y + TILE_H / 2 - 22;
                    ctx.strokeStyle = '#4c566a';
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    ctx.moveTo(cx, cy);
                    ctx.lineTo(cx, cy - 8);
                    ctx.stroke();
                    
                    // Screen bezel
                    ctx.fillStyle = '#2e3440';
                    ctx.fillRect(cx - 5, cy - 14, 10, 6);
                    ctx.strokeStyle = '#d8dee9';
                    ctx.strokeRect(cx - 5, cy - 14, 10, 6);
                    // Green screen text
                    ctx.fillStyle = '#a3be8c';
                    ctx.fillRect(cx - 4, cy - 13, 8, 4);
                }
                if (x === MAP_BLOCKS.counter.x && y === MAP_BLOCKS.counter.y - 1) {
                    // Second part of counter desk
                    draw3DBox(x, y, 1.0, 18, '#4c566a', '#e5e9f0', '#3b4252');
                }
                
                // Storage Area cardboard box stacks with tape
                if (x === MAP_BLOCKS.storage.x && y === MAP_BLOCKS.storage.y) {
                    drawDetailedBox(x, y, 0.8, 12, 0, -4, -2); // Box 1
                    drawDetailedBox(x, y, 0.65, 10, 12, 4, 2); // Box 2
                    drawDetailedBox(x, y, 0.6, 9, 0, 12, 6);   // Box 3
                }
                
                // Multi-tier Wooden Shelves
                for (const shelf of MAP_BLOCKS.shelves) {
                    if (x === shelf.x && y === shelf.y) {
                        // Base wooden board
                        draw3DBox(x, y, 0.8, 3, '#8a653e', '#b6926b', '#5e3e29', 0);
                        // Middle board
                        draw3DBox(x, y, 0.8, 3, '#8a653e', '#b6926b', '#5e3e29', 12);
                        // Top board
                        draw3DBox(x, y, 0.8, 3, '#8a653e', '#b6926b', '#5e3e29', 24);
                        
                        // Check backend stock level of product category to render products on the shelves!
                        const matchedProduct = products.find(p => p.category === shelf.id);
                        if (matchedProduct && matchedProduct.stockQuantity > 0) {
                            // Tier 1 products (Bottom)
                            if (matchedProduct.stockQuantity > 0) {
                                drawMiniProduct(shelf.id, x, y, 3, -10, -5);
                                drawMiniProduct(shelf.id, x, y, 3, 0, 0);
                                drawMiniProduct(shelf.id, x, y, 3, 10, 5);
                            }
                            // Tier 2 products (Middle)
                            if (matchedProduct.stockQuantity > 10) {
                                drawMiniProduct(shelf.id, x, y, 15, -8, -4);
                                drawMiniProduct(shelf.id, x, y, 15, 8, 4);
                            }
                            // Tier 3 products (Top)
                            if (matchedProduct.stockQuantity > 25) {
                                drawMiniProduct(shelf.id, x, y, 27, 0, 0);
                            }
                        }
                    }
                }
                
                // Decorative Props
                for (const prop of MAP_BLOCKS.props) {
                    if (x === prop.x && y === prop.y) {
                        if (prop.type === 'plant') {
                            // Terracotta plant pot
                            draw3DBox(x, y, 0.45, 8, '#d08770', '#e09d84', '#b86d52');
                            // Green leaves branching out
                            const pt = isoToScreen(x, y);
                            const cx = pt.x;
                            const cy = pt.y + TILE_H / 2 - 8;
                            ctx.fillStyle = '#a3be8c';
                            ctx.beginPath();
                            ctx.arc(cx, cy - 4, 6, 0, Math.PI*2);
                            ctx.arc(cx - 5, cy, 5, 0, Math.PI*2);
                            ctx.arc(cx + 5, cy, 5, 0, Math.PI*2);
                            ctx.arc(cx, cy + 3, 5, 0, Math.PI*2);
                            ctx.fill();
                            // Highlights
                            ctx.fillStyle = '#8fbcbb';
                            ctx.beginPath();
                            ctx.arc(cx - 2, cy - 2, 2, 0, Math.PI*2);
                            ctx.fill();
                        } else if (prop.type === 'trash') {
                            // Silver shiny trash can
                            draw3DBox(x, y, 0.45, 12, '#d8dee9', '#e5e9f0', '#4c566a');
                            // Bag rim
                            draw3DBox(x, y, 0.48, 1.5, '#2e3440', '#3b4252', '#2e3440', 12);
                        }
                    }
                }
                
                // 4. Draw active characters standing or walking on this grid cell
                
                // Customers
                localCustomers.forEach(c => {
                    if (Math.floor(c.x) === x && Math.floor(c.y) === y) {
                        drawCharacter(c);
                    }
                });
                
                // Delivery Worker
                if (activeDelivery && activeDelivery.worker) {
                    const dw = activeDelivery.worker;
                    if (Math.floor(dw.x) === x && Math.floor(dw.y) === y) {
                        drawCharacter(dw);
                    }
                }

                // Cashier standing behind counter at (13, 3)
                if (x === cashier.x && y === cashier.y) {
                    drawCharacter(cashier);
                }
            }
        }
    }
    
    // Restore camera translation matrices
    ctx.restore();
    
    // Draw Delivery Truck parking lot overlay
    drawDeliveryTruck();
    
    // Update Simulation movements
    updateSimulationLogic();
    
    requestAnimationFrame(updateAndRender);
}

// --- STATE MACHINE MOVEMENTS & ACTIONS ---
function updateSimulationLogic() {
    // 1. Move PNJ Customers
    localCustomers.forEach(c => {
        // Handle active emotes timers
        if (c.emote) {
            c.emoteTimer--;
            if (c.emoteTimer <= 0) c.emote = null;
        }

        // If wait timer is active, decrease it
        if (c.waitTimer > 0) {
            c.waitTimer--;
            return;
        }
        
        // If they need a path to their target, calculate it
        const currentTarget = c.targets[c.currentTargetIndex];
        if (currentTarget && (!c.path || c.path.length === 0)) {
            // Find path to target standing spot
            let tx = currentTarget.x;
            let ty = currentTarget.y;
            
            // If going to register, adjust spot based on queue index
            if (currentTarget.type === 'checkout') {
                const idx = checkoutQueue.indexOf(c.id);
                if (idx !== -1) {
                    // Line up behind each other (7, 2), (7, 3), (7, 4)...
                    ty = MAP_BLOCKS.counter.standY + idx;
                }
            }
            
            c.path = findPath(Math.round(c.x), Math.round(c.y), tx, ty);
            c.pathIndex = 0;
        }
        
        // Move along path
        if (c.path && c.pathIndex < c.path.length) {
            const nextNode = c.path[c.pathIndex];
            const nx = nextNode[0];
            const ny = nextNode[1];
            
            const dx = nx - c.x;
            const dy = ny - c.y;
            const dist = Math.hypot(dx, dy);
            
            if (dist < c.speed) {
                // Snap to node
                c.x = nx;
                c.y = ny;
                c.pathIndex++;
                
                // If reached end of path
                if (c.pathIndex >= c.path.length) {
                    c.path = null;
                    handleTargetReached(c);
                }
            } else {
                c.x += (dx / dist) * c.speed;
                c.y += (dy / dist) * c.speed;
            }
        }
    });
    
    // 2. Manage Delivery sequence state machine
    updateDeliveryLogic();
    
    // 3. Auto PNJ Spawner (if simulation running on backend)
    if (simulationActive) {
        const now = Date.now();
        if (now - lastSpawnTime > 5000 && localCustomers.length < 5) {
            lastSpawnTime = now;
            generatePNJCustomer();
        }
    }
}

// When a PNJ Customer reaches their temporary target node
function handleTargetReached(c) {
    const currentTarget = c.targets[c.currentTargetIndex];
    if (!currentTarget) return;
    
    if (currentTarget.type === 'shelf') {
        // Simulated shelf interaction
        c.emote = '🛒';
        c.emoteTimer = 60;
        c.waitTimer = 60; // Wait 1 second
        addLog(`${c.name} examine le rayon ${currentTarget.item.name}`, 'cart');
        c.currentTargetIndex++;
        
    } else if (currentTarget.type === 'checkout') {
        // Reached cashier area
        const queueIdx = checkoutQueue.indexOf(c.id);
        
        // If not in the queue array yet, join it
        if (queueIdx === -1) {
            checkoutQueue.push(c.id);
            c.emote = '🕒';
            c.emoteTimer = 60;
            addLog(`${c.name} fait la queue à la caisse`, 'system');
        } else if (queueIdx === 0) {
            // First in line! Initiate API Checkout call
            c.waitTimer = 100; // Trigger delay during API call
            performCheckout(c);
        } else {
            // Waiting in queue: recalculate path to stay in line if queue shifted
            c.path = null; 
        }
        
    } else if (currentTarget.type === 'exit') {
        // Reached door, exit simulation
        addLog(`Client ${c.name} est sorti du magasin`, 'system');
        
        // Clean customer from backend API
        deleteCustomerFromBackend(c.id);
        
        // Remove locally
        localCustomers = localCustomers.filter(cust => cust.id !== c.id);
    }
}

// Perform checkout API transaction call
async function performCheckout(c) {
    try {
        const res = await fetch(`${API_BASE}/transactions/checkout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ customerId: c.id })
        });
        
        if (res.status === 201) {
            const data = await res.json();
            
            // Successful transaction
            c.emote = '❤️'; // Happy
            c.emoteTimer = 120;
            addLog(`[CAISSE] Achat réussi pour ${c.name} : +${data.total.toFixed(2)}€`, 'checkout');
            
            // Remove from queue
            checkoutQueue.shift();
            
            // Move to exit target
            c.currentTargetIndex++;
            c.path = null;
            
            // Update sidebar stats
            await fetchStatus();
            
        } else {
            // Read error response
            const errData = await res.json();
            throw new Error(errData.message || 'Checkout failed');
        }
        
    } catch (err) {
        // Budget or stock failure
        c.emote = '💢'; // Angry!
        c.emoteTimer = 120;
        c.speed = 0.08; // Walks faster because angry!
        addLog(`[CAISSE] Achat échoué pour ${c.name} : ${err.message}`, 'error');
        
        // Remove from queue
        checkoutQueue.shift();
        
        // Skip remaining targets and go straight to exit
        c.currentTargetIndex = c.targets.length - 1; // Exit node is last
        c.path = null;
    }
}

async function deleteCustomerFromBackend(id) {
    try {
        await fetch(`${API_BASE}/customers/${id}`, { method: 'DELETE' });
    } catch (err) {
        console.error('Error deleting customer:', err);
    }
}

// --- DELIVERY worker logic ---
function updateDeliveryLogic() {
    if (!activeDelivery) return;
    
    const d = activeDelivery;
    
    switch (d.state) {
        case 'TRUCK_ARRIVING':
            // Slide truck in
            d.truckAnim += 0.015;
            if (d.truckAnim >= 1.0) {
                d.truckAnim = 1.0;
                d.state = 'WORKER_ENTERING';
                
                // Spawn worker carrying box
                d.worker = {
                    name: 'Livreur',
                    color: '#d08770', // Orange uniform
                    x: MAP_BLOCKS.door.x,
                    y: MAP_BLOCKS.door.y,
                    speed: 0.05,
                    carrying: true,
                    emote: '📦',
                    emoteTimer: 120,
                    budget: 0,
                    path: findPath(MAP_BLOCKS.door.x, MAP_BLOCKS.door.y, MAP_BLOCKS.storage.standX, MAP_BLOCKS.storage.standY),
                    pathIndex: 0
                };
            }
            break;
            
        case 'WORKER_ENTERING':
            // Move worker to storage zone
            moveDeliveryWorker(d.worker, () => {
                d.state = 'DROPPING_STORAGE';
                d.timer = 90; // Wait 1.5 seconds to drop boxes
                d.worker.emote = '✅';
                d.worker.emoteTimer = 60;
                addLog(`Livreur dépose les cartons dans la zone de stockage`, 'restock');
            });
            break;
            
        case 'DROPPING_STORAGE':
            d.timer--;
            if (d.timer <= 0) {
                d.worker.carrying = false; // Dropped box
                
                // Find shelf destination corresponding to product category
                const targetShelf = MAP_BLOCKS.shelves.find(s => s.id === d.category);
                let tx = MAP_BLOCKS.counter.standX;
                let ty = MAP_BLOCKS.counter.standY;
                if (targetShelf) {
                    tx = targetShelf.standX;
                    ty = targetShelf.standY;
                }
                
                // Move worker to shelf
                d.worker.path = findPath(Math.round(d.worker.x), Math.round(d.worker.y), tx, ty);
                d.worker.pathIndex = 0;
                d.state = 'RESTOCKING_SHELF';
            }
            break;
            
        case 'RESTOCKING_SHELF':
            moveDeliveryWorker(d.worker, () => {
                // Arrived at shelf: perform REST delivery call to update backend stock!
                deliverRestockOnBackend(d);
            });
            break;
            
        case 'WORKER_EXITING':
            // Move worker back to door
            moveDeliveryWorker(d.worker, () => {
                // Worker exited store: drive truck away
                d.state = 'TRUCK_LEAVING';
                d.worker = null;
            });
            break;
            
        case 'TRUCK_LEAVING':
            // Slide truck out to the right side
            d.truckAnim -= 0.015;
            if (d.truckAnim <= 0) {
                addLog(`[LIVRAISON] Camion reparti`, 'system');
                activeDelivery = null; // Sequence complete!
            }
            break;
    }
}

function moveDeliveryWorker(worker, onArrival) {
    if (worker.emoteTimer > 0) {
        worker.emoteTimer--;
        if (worker.emoteTimer <= 0) worker.emote = null;
    }
    
    if (worker.path && worker.pathIndex < worker.path.length) {
        const node = worker.path[worker.pathIndex];
        const dx = node[0] - worker.x;
        const dy = node[1] - worker.y;
        const dist = Math.hypot(dx, dy);
        
        if (dist < worker.speed) {
            worker.x = node[0];
            worker.y = node[1];
            worker.pathIndex++;
            if (worker.pathIndex >= worker.path.length) {
                worker.path = null;
                onArrival();
            }
        } else {
            worker.x += (dx / dist) * worker.speed;
            worker.y += (dy / dist) * worker.speed;
        }
    }
}

async function deliverRestockOnBackend(d) {
    try {
        const res = await fetch(`${API_BASE}/products/restock/${d.orderId}/deliver`, { method: 'POST' });
        if (res.ok) {
            addLog(`[LIVRAISON] Étagères de ${d.productName} rechargées (+50 unités)`, 'checkout');
            
            // Sync products list immediately
            await fetchStatus();
            
            // Worker exits
            d.worker.path = findPath(Math.round(d.worker.x), Math.round(d.worker.y), MAP_BLOCKS.door.x, MAP_BLOCKS.door.y);
            d.worker.pathIndex = 0;
            d.state = 'WORKER_EXITING';
        }
    } catch (err) {
        console.error('Error delivering restock:', err);
    }
}

// --- BUTTONS LISTENERS ---
btnStart.addEventListener('click', startSimulation);
btnStop.addEventListener('click', stopSimulation);
btnReset.addEventListener('click', resetSimulation);
btnTick.addEventListener('click', tickSimulation);
btnClearLogs.addEventListener('click', () => {
    consoleLogs.innerHTML = '';
});

// --- INITIALIZATION ---
async function init() {
    addLog('Connexion au serveur...', 'system');
    await fetchStatus();
    addLog('Connecté ! Catalogue chargé.', 'system');
    
    // Start polling sync loop every 1.5 seconds
    syncInterval = setInterval(fetchStatus, 1500);
    
    // Start 60fps rendering canvas loop
    requestAnimationFrame(updateAndRender);
}

init();
