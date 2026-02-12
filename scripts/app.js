const API_URL = 'http://localhost:8080/api';
let menuItems = [];
let cart = {};
let currentFilter = 'All';
let imageManifest = {};
let adminKey = null;
let isAdmin = false;

// Initialize the application
async function init() {
    await loadManifest();
    await loadMenu();
    setupEventListeners();
}

// Load image manifest
async function loadManifest() {
    try {
        const resp = await fetch('images/manifest.json');
        if (resp.ok) {
            imageManifest = await resp.json();
            console.log('[Images] Manifest loaded:', Object.keys(imageManifest).length, 'entries');
        } else {
            console.warn('[Images] Manifest not found');
        }
    } catch (e) {
        console.warn('[Images] Failed to load manifest:', e.message);
    }
}

// Load menu items from API
async function loadMenu() {
    try {
        console.log("Loading menu from API...");
        const response = await fetch(`${API_URL}/menu`);
        console.log("API Response status:", response.status);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: Failed to load menu`);
        }

        menuItems = await response.json();
        console.log("Menu loaded successfully:", menuItems.length, "items");
        renderMenu(menuItems);
    } catch (error) {
        console.error('Error loading menu:', error);
        document.getElementById('menu-container').innerHTML = 
            '<div class="loading" style="color: red; padding: 40px; text-align: center;">' +
            '<strong>Error loading menu</strong><br/>' +
            'Make sure the server is running on port 8080<br/>' +
            'Error: ' + error.message + '<br/>' +
            'Open browser console (F12) for more details</div>';
    }
}

// Render menu items in the grid
function renderMenu(items) {
    const container = document.getElementById('menu-container');
    if (items.length === 0) {
        container.innerHTML = '<div class="loading">No items found</div>';
        return;
    }
    container.innerHTML = items.map(item => {
        const nameEncoded = encodeURIComponent(item.name);
        const sanitized = item.name.replace(/[^a-z0-9]/gi, '_').toLowerCase();
        let imgSrc = imageManifest[sanitized] ? `images/${imageManifest[sanitized]}` : `images/${item.id}.jpg`;
        return `
            <div class="menu-card">
                <div class="card-image"><img src="${imgSrc}" alt="${item.name}" onerror="handleImageError(this, '${item.id}', '${nameEncoded}', '${item.category}')"></div>
                <div class="card-name">${item.name}</div>
                <div class="card-category">${item.category}</div>
                <div class="card-description">${item.description}</div>
                <div class="card-footer">
                    <span class="card-price">$${item.price.toFixed(2)}</span>
                    <button class="btn-add" onclick="addToCart(${item.id}, '${item.name.replace(/'/g, "\\'")}', ${item.price})">
                        Add to Order
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

// Try alternate filenames when image 404s: id.{ext} -> name.{ext} -> category fallback
function handleImageError(img, id, nameEncoded, category) {
    const attempt = img.dataset.attempt ? parseInt(img.dataset.attempt, 10) : 0;
    const exts = ['jpg','png','jpeg','webp','svg'];
    if (attempt < exts.length) {
        img.dataset.attempt = attempt + 1;
        img.src = `images/${id}.${exts[attempt]}`;
        return;
    }

    const nameAttempt = img.dataset.nameAttempt ? parseInt(img.dataset.nameAttempt, 10) : 0;
    const decodedName = decodeURIComponent(nameEncoded);
    const sanitized = decodedName.replace(/[^a-z0-9]/gi, '_').toLowerCase();
    if (nameAttempt < exts.length) {
        img.dataset.nameAttempt = nameAttempt + 1;
        img.src = `images/${sanitized}.${exts[nameAttempt]}`;
        return;
    }

    img.onerror = null;
    img.src = getCategoryImage(category);
}

function getCategoryImage(category) {
    const images = {
        'Appetizer': 'images/appetizer.svg',
        'Main': 'images/main.svg',
        'Dessert': 'images/dessert.svg',
        'Drink': 'images/drink.svg'
    };
    return images[category] || 'images/food.svg';
}

// Add item to cart
function addToCart(id, name, price) {
    if (!cart[id]) {
        cart[id] = { name, price, quantity: 0 };
    }
    cart[id].quantity++;
    updateCart();
    showNotification(`${name} added to cart!`, 'success');
}

// Remove item from cart
function removeFromCart(id) {
    if (cart[id]) {
        cart[id].quantity--;
        if (cart[id].quantity <= 0) {
            delete cart[id];
        }
        updateCart();
    }
}

// Update cart display
function updateCart() {
    const cartItemsDiv = document.getElementById('cart-items');
    const cartItems = Object.entries(cart);

    if (cartItems.length === 0) {
        cartItemsDiv.innerHTML = '<div class="cart-empty">Your cart is empty</div>';
        document.getElementById('cart-count').textContent = '0';
        document.getElementById('cart-subtotal').textContent = '$0.00';
        document.getElementById('cart-tax').textContent = '$0.00';
        document.getElementById('cart-total').textContent = '$0.00';
        return;
    }

    cartItemsDiv.innerHTML = cartItems.map(([id, item]) => `
        <div class="cart-item">
            <div class="cart-item-info">
                <div class="cart-item-name">${item.name}</div>
                <div class="cart-item-price">$${item.price.toFixed(2)} × ${item.quantity}</div>
            </div>
            <button class="btn-remove" onclick="removeFromCart(${id})">Remove</button>
        </div>
    `).join('');

    let totalItems = 0;
    let subtotal = 0;
    cartItems.forEach(([_, item]) => {
        totalItems += item.quantity;
        subtotal += item.price * item.quantity;
    });

    const tax = subtotal * 0.1;
    const total = subtotal + tax;

    document.getElementById('cart-count').textContent = totalItems;
    document.getElementById('cart-subtotal').textContent = `$${subtotal.toFixed(2)}`;
    document.getElementById('cart-tax').textContent = `$${tax.toFixed(2)}`;
    document.getElementById('cart-total').textContent = `$${total.toFixed(2)}`;
}

// Place order
async function placeOrder() {
    const customerName = document.getElementById('customer-name').value.trim();
    
    if (!customerName) {
        showNotification('Please enter your name', 'error');
        return;
    }

    if (Object.keys(cart).length === 0) {
        showNotification('Your cart is empty', 'error');
        return;
    }

    const orderItems = Object.entries(cart).map(([id, item]) => ({
        menuItemId: parseInt(id),
        quantity: item.quantity
    }));

    const orderData = {
        customerName: customerName,
        items: orderItems
    };

    try {
        const response = await fetch(`${API_URL}/orders`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(orderData)
        });

        if (!response.ok) {
            throw new Error('Failed to place order');
        }

        const result = await response.json();
        showNotification(`Order #${result.orderId} placed successfully! Thank you ${customerName}!`, 'success');
        
        cart = {};
        document.getElementById('customer-name').value = '';
        updateCart();
        
        setTimeout(() => {
            document.getElementById('notification').innerHTML = '';
        }, 3000);
    } catch (error) {
        console.error('Error placing order:', error);
        showNotification('Error placing order. Please try again.', 'error');
    }
}

// Show notification
function showNotification(message, type) {
    const notifDiv = document.getElementById('notification');
    notifDiv.innerHTML = `<div class="notification ${type}">${message}</div>`;
}

// Setup event listeners
function setupEventListeners() {
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            e.target.classList.add('active');
            currentFilter = e.target.dataset.category;
            
            const filtered = currentFilter === 'All' 
                ? menuItems 
                : menuItems.filter(item => item.category === currentFilter);
            renderMenu(filtered);
        });
    });

    document.getElementById('place-order-btn').addEventListener('click', placeOrder);

    document.getElementById('customer-name').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') placeOrder();
    });

    // Admin buttons
    const loginBtn = document.getElementById('admin-login-btn');
    const logoutBtn = document.getElementById('admin-logout-btn');
    if (loginBtn) loginBtn.addEventListener('click', adminLogin);
    if (logoutBtn) logoutBtn.addEventListener('click', adminLogout);

    const createBtn = document.getElementById('menu-create-btn');
    const updateBtn = document.getElementById('menu-update-btn');
    const deleteBtn = document.getElementById('menu-delete-btn');
    if (createBtn) createBtn.addEventListener('click', adminCreateMenuItem);
    if (updateBtn) updateBtn.addEventListener('click', adminUpdateMenuItem);
    if (deleteBtn) deleteBtn.addEventListener('click', adminDeleteMenuItem);
}

async function adminLogin() {
    const keyInput = document.getElementById('admin-key');
    if (!keyInput) return;
    adminKey = keyInput.value.trim();
    if (!adminKey) {
        alert('Enter admin key');
        return;
    }
    isAdmin = true;
    document.getElementById('admin-status').textContent = 'Logged in as admin';
    document.getElementById('admin-login-btn').style.display = 'none';
    document.getElementById('admin-logout-btn').style.display = 'inline-block';
    document.getElementById('admin-area').style.display = 'block';
    await fetchOrders();
}

function adminLogout() {
    adminKey = null;
    isAdmin = false;
    document.getElementById('admin-status').textContent = 'Not logged in';
    document.getElementById('admin-login-btn').style.display = 'inline-block';
    document.getElementById('admin-logout-btn').style.display = 'none';
    document.getElementById('admin-area').style.display = 'none';
}

async function fetchOrders() {
    try {
        const resp = await fetch(`${API_URL}/orders`);
        if (!resp.ok) throw new Error('Failed to load orders');
        const orders = await resp.json();
        renderOrdersTable(orders);
    } catch (e) {
        console.error('fetchOrders error:', e);
    }
}

function renderOrdersTable(orders) {
    const tbody = document.querySelector('#orders-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    orders.forEach(o => {
        const itemsText = (o.items || []).map(i => `${i.name}×${i.quantity || 1}`).join(', ');
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td style="padding:6px;border-bottom:1px solid #eee">${o.id}</td>
            <td style="padding:6px;border-bottom:1px solid #eee">${o.customer}</td>
            <td style="padding:6px;border-bottom:1px solid #eee">${itemsText}</td>
            <td style="padding:6px;border-bottom:1px solid #eee">$${(o.price||0).toFixed(2)}</td>
            <td style="padding:6px;border-bottom:1px solid #eee;text-align:center"><button data-id="${o.id}" class="admin-delete-order">Delete</button></td>
        `;
        tbody.appendChild(tr);
    });

    // wire delete buttons
    document.querySelectorAll('.admin-delete-order').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            const id = e.target.dataset.id;
            if (!confirm('Delete order #' + id + '?')) return;
            try {
                const resp = await fetch(`${API_URL}/orders/${id}`, {
                    method: 'DELETE',
                    headers: { 'X-Admin-Key': adminKey }
                });
                if (!resp.ok) throw new Error('Delete failed');
                await fetchOrders();
            } catch (err) {
                alert('Failed to delete order: ' + err.message);
            }
        });
    });
}

async function adminCreateMenuItem() {
    if (!isAdmin) return alert('Not admin');
    const name = document.getElementById('menu-name').value.trim();
    const desc = document.getElementById('menu-desc').value.trim();
    const category = document.getElementById('menu-category').value.trim();
    const price = parseFloat(document.getElementById('menu-price').value);
    if (!name) return alert('Name required');
    try {
        const resp = await fetch(`${API_URL}/menu`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Key': adminKey },
            body: JSON.stringify({ name, description: desc, category, price })
        });
        if (!resp.ok) throw new Error('Create failed');
        await loadMenu();
        alert('Menu item created');
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

async function adminUpdateMenuItem() {
    if (!isAdmin) return alert('Not admin');
    const id = parseInt(document.getElementById('menu-id').value);
    const name = document.getElementById('menu-name').value.trim();
    const desc = document.getElementById('menu-desc').value.trim();
    const category = document.getElementById('menu-category').value.trim();
    const price = parseFloat(document.getElementById('menu-price').value);
    if (!id) return alert('ID required for update');
    try {
        const resp = await fetch(`${API_URL}/menu/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Key': adminKey },
            body: JSON.stringify({ name, description: desc, category, price })
        });
        if (!resp.ok) throw new Error('Update failed');
        await loadMenu();
        alert('Menu item updated');
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

async function adminDeleteMenuItem() {
    if (!isAdmin) return alert('Not admin');
    const id = parseInt(document.getElementById('menu-id').value);
    if (!id) return alert('ID required to delete');
    if (!confirm('Delete menu item #' + id + '?')) return;
    try {
        const resp = await fetch(`${API_URL}/menu/${id}`, {
            method: 'DELETE',
            headers: { 'X-Admin-Key': adminKey }
        });
        if (!resp.ok) throw new Error('Delete failed');
        await loadMenu();
        alert('Menu item deleted');
    } catch (e) {
        alert('Error: ' + e.message);
    }
}

// Start the app
init();
