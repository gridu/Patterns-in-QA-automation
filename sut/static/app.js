// State Management
const state = {
  currentUser: null,
  cart: { items: [], total_price: 0 },
  products: [],
  selectedProduct: null,
  authMode: 'login', // 'login' | 'register'
};

// API Helper
async function apiRequest(endpoint, options = {}) {
  const defaultHeaders = {
    'Content-Type': 'application/json',
  };
  const response = await fetch(endpoint, {
    ...options,
    headers: {
      ...defaultHeaders,
      ...(options.headers || {}),
    },
  });

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const errorMsg = data && data.detail ? data.detail : 'Request failed';
    throw new Error(errorMsg);
  }
  return data;
}

// Notifications
function showNotification(message) {
  const banner = document.getElementById('notification-banner');
  banner.textContent = message;
  banner.classList.remove('hidden');
  setTimeout(() => {
    banner.classList.add('hidden');
  }, 4000);
}

// View Switching
function showView(viewId) {
  ['catalog-view', 'product-detail-view', 'cart-container'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.add('hidden');
  });
  const target = document.getElementById(viewId);
  if (target) target.classList.remove('hidden');
}

// Authentication UI
function updateAuthUI() {
  const guestActions = document.getElementById('guest-actions');
  const userActions = document.getElementById('user-actions');
  const usernameSpan = document.getElementById('current-username');

  if (state.currentUser) {
    guestActions.classList.add('hidden');
    userActions.classList.remove('hidden');
    usernameSpan.textContent = state.currentUser.username;
  } else {
    guestActions.classList.remove('hidden');
    userActions.classList.add('hidden');
    usernameSpan.textContent = '';
  }

  // Update Cart warning & purchase button if cart is open
  updateCartAuthStatus();
}

function updateCartBadge() {
  const badge = document.getElementById('cart-count-badge');
  const count = state.cart.items.reduce((sum, item) => sum + item.quantity, 0);
  badge.textContent = count;
}

// Products Catalog
async function loadProducts() {
  const sortSelect = document.getElementById('catalog-sort-select');
  const [sort_by, order] = (sortSelect.value || 'name-asc').split('-');

  try {
    const products = await apiRequest(`/api/products?sort_by=${sort_by}&order=${order}`);
    state.products = products;
    renderCatalog();
  } catch (err) {
    console.error('Failed to load products:', err);
  }
}

function renderCatalog() {
  const container = document.getElementById('catalog-container');
  container.innerHTML = '';

  if (state.products.length === 0) {
    container.innerHTML = '<p style="color: var(--text-muted); grid-column: 1 / -1;">No products available in catalog.</p>';
    return;
  }

  state.products.forEach(product => {
    const card = document.createElement('div');
    card.className = 'product-card';
    card.setAttribute('data-testid', `product-card-${product.id}`);

    card.innerHTML = `
      <div class="product-card-title" data-testid="product-name-${product.id}">
        ${escapeHtml(product.name)}
      </div>
      <div class="product-card-price" data-testid="product-price-${product.id}">
        $${Number(product.price).toFixed(2)}
      </div>
      <div class="product-card-stock">
        Available: ${product.quantity}
      </div>
      <button class="btn btn-primary" data-testid="add-to-cart-btn-${product.id}">
        Add to Cart
      </button>
    `;

    // Title click opens detail
    card.querySelector(`[data-testid="product-name-${product.id}"]`).addEventListener('click', () => {
      openProductDetail(product.id);
    });

    // Add to cart click
    card.querySelector(`[data-testid="add-to-cart-btn-${product.id}"]`).addEventListener('click', (e) => {
      e.stopPropagation();
      addToCart(product.id, 1);
    });

    container.appendChild(card);
  });
}

// Product Details
async function openProductDetail(productId) {
  try {
    const product = await apiRequest(`/api/products/${productId}`);
    state.selectedProduct = product;

    document.getElementById('product-detail-name').textContent = product.name;
    document.getElementById('product-detail-description').textContent = product.description || 'No description available.';
    document.getElementById('product-detail-price').textContent = `$${Number(product.price).toFixed(2)}`;
    document.getElementById('product-detail-quantity').textContent = `Available Stock: ${product.quantity}`;

    const input = document.getElementById('product-detail-quantity-input');
    input.value = 1;
    input.min = 1;
    input.max = product.quantity;

    validateDetailQuantity();
    showView('product-detail-view');
  } catch (err) {
    alert(`Error: ${err.message}`);
  }
}

function validateDetailQuantity() {
  const input = document.getElementById('product-detail-quantity-input');
  const errorMsg = document.getElementById('quantity-error-msg');
  const addBtn = document.getElementById('product-detail-add-btn');

  if (!state.selectedProduct) return;

  const max = state.selectedProduct.quantity;
  const val = parseInt(input.value, 10);

  if (isNaN(val) || val < 1) {
    errorMsg.textContent = 'Please enter a valid quantity of at least 1.';
    errorMsg.classList.remove('hidden');
    addBtn.disabled = true;
  } else if (val > max) {
    errorMsg.textContent = `Quantity cannot exceed available stock of ${max}.`;
    errorMsg.classList.remove('hidden');
    addBtn.disabled = true;
  } else {
    errorMsg.textContent = '';
    errorMsg.classList.add('hidden');
    addBtn.disabled = false;
  }
}

// Cart Operations
async function loadCart() {
  try {
    const cart = await apiRequest('/api/cart');
    state.cart = cart;
    updateCartBadge();
    renderCart();
  } catch (err) {
    console.error('Failed to load cart:', err);
  }
}

async function addToCart(productId, quantity) {
  try {
    const updatedCart = await apiRequest('/api/cart/items', {
      method: 'POST',
      body: JSON.stringify({ product_id: productId, quantity }),
    });
    state.cart = updatedCart;
    updateCartBadge();
    showNotification('Product added to cart!');
  } catch (err) {
    alert(err.message);
  }
}

async function deleteCartItem(productId) {
  try {
    await apiRequest(`/api/cart/items/${productId}`, { method: 'DELETE' });
    await loadCart();
  } catch (err) {
    alert(err.message);
  }
}

function renderCart() {
  const tbody = document.getElementById('cart-table-body');
  const totalEl = document.getElementById('cart-total-price');
  const itemsWrapper = document.getElementById('cart-items-wrapper');
  const emptyMsg = document.getElementById('empty-cart-message');

  tbody.innerHTML = '';

  if (state.cart.items.length === 0) {
    itemsWrapper.classList.add('hidden');
    emptyMsg.classList.remove('hidden');
    totalEl.textContent = '$0.00';
    return;
  }

  itemsWrapper.classList.remove('hidden');
  emptyMsg.classList.add('hidden');

  state.cart.items.forEach(item => {
    const tr = document.createElement('tr');
    tr.setAttribute('data-testid', `cart-item-${item.product_id}`);

    tr.innerHTML = `
      <td><strong>${escapeHtml(item.name)}</strong></td>
      <td>$${Number(item.price).toFixed(2)}</td>
      <td>${item.quantity}</td>
      <td data-testid="cart-item-subtotal-${item.product_id}">$${Number(item.subtotal).toFixed(2)}</td>
      <td>
        <button class="btn btn-danger" data-testid="delete-cart-item-btn-${item.product_id}">
          Delete
        </button>
      </td>
    `;

    tr.querySelector(`[data-testid="delete-cart-item-btn-${item.product_id}"]`).addEventListener('click', () => {
      deleteCartItem(item.product_id);
    });

    tbody.appendChild(tr);
  });

  totalEl.textContent = `$${Number(state.cart.total_price).toFixed(2)}`;
  updateCartAuthStatus();
}

function updateCartAuthStatus() {
  const warning = document.getElementById('cart-auth-warning');
  const purchaseBtn = document.getElementById('purchase-btn');

  const hasItems = state.cart.items.length > 0;
  const isAuthenticated = !!state.currentUser;

  if (isAuthenticated) {
    warning.classList.add('hidden');
    purchaseBtn.disabled = !hasItems;
  } else {
    warning.classList.remove('hidden');
    purchaseBtn.disabled = true;
  }
}

async function handleCheckout() {
  try {
    const result = await apiRequest('/api/cart/checkout', { method: 'POST' });
    showNotification(`${result.detail}! Total: $${Number(result.order_total).toFixed(2)}`);
    await loadCart();
    await loadProducts();
    showView('catalog-view');
  } catch (err) {
    alert(err.message);
  }
}

// Authentication Modal
function openAuthModal(mode = 'login') {
  state.authMode = mode;
  const modal = document.getElementById('auth-modal');
  const title = document.getElementById('auth-modal-title');
  const submitBtn = document.getElementById('auth-submit-btn');
  const switchText = document.getElementById('auth-switch-text');
  const switchBtn = document.getElementById('auth-switch-mode-btn');
  const errorMsg = document.getElementById('auth-error-msg');

  errorMsg.classList.add('hidden');
  errorMsg.textContent = '';
  document.getElementById('auth-username-input').value = '';
  document.getElementById('auth-password-input').value = '';

  if (mode === 'login') {
    title.textContent = 'Login';
    submitBtn.textContent = 'Login';
    switchText.textContent = "Don't have an account?";
    switchBtn.textContent = 'Register';
  } else {
    title.textContent = 'Create Account';
    submitBtn.textContent = 'Register';
    switchText.textContent = 'Already have an account?';
    switchBtn.textContent = 'Login';
  }

  modal.classList.remove('hidden');
}

function closeAuthModal() {
  document.getElementById('auth-modal').classList.add('hidden');
}

async function handleAuthSubmit(e) {
  e.preventDefault();
  const username = document.getElementById('auth-username-input').value.trim();
  const password = document.getElementById('auth-password-input').value;
  const errorMsg = document.getElementById('auth-error-msg');

  const endpoint = state.authMode === 'login' ? '/api/auth/login' : '/api/auth/register';

  try {
    const user = await apiRequest(endpoint, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });

    state.currentUser = user;
    closeAuthModal();
    updateAuthUI();

    // Re-fetch cart as any guest items have merged into user cart
    await loadCart();
    showNotification(`Welcome, ${user.username}!`);
  } catch (err) {
    errorMsg.textContent = err.message;
    errorMsg.classList.remove('hidden');
  }
}

async function handleLogout() {
  try {
    await apiRequest('/api/auth/logout', { method: 'POST' });
    state.currentUser = null;
    updateAuthUI();
    await loadCart();
    showNotification('Logged out successfully.');
  } catch (err) {
    alert(err.message);
  }
}

// Utility
function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&<>'"]/g, tag => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;',
  }[tag] || tag));
}

// Event Listeners Initialization
function initEventListeners() {
  // Navigation
  document.getElementById('brand-link').addEventListener('click', (e) => {
    e.preventDefault();
    showView('catalog-view');
  });

  document.getElementById('nav-cart-btn').addEventListener('click', () => {
    showView('cart-container');
    renderCart();
  });

  document.getElementById('cart-back-btn').addEventListener('click', () => {
    showView('catalog-view');
  });

  document.getElementById('nav-login-btn').addEventListener('click', () => openAuthModal('login'));
  document.getElementById('nav-register-btn').addEventListener('click', () => openAuthModal('register'));
  document.getElementById('nav-logout-btn').addEventListener('click', handleLogout);

  document.getElementById('cart-login-link').addEventListener('click', () => openAuthModal('login'));
  document.getElementById('cart-register-link').addEventListener('click', () => openAuthModal('register'));

  // Catalog Controls
  document.getElementById('catalog-sort-select').addEventListener('change', loadProducts);

  // Product Detail Controls
  document.getElementById('back-to-catalog-btn').addEventListener('click', () => {
    showView('catalog-view');
  });

  document.getElementById('product-detail-quantity-input').addEventListener('input', validateDetailQuantity);

  document.getElementById('product-detail-add-btn').addEventListener('click', () => {
    if (!state.selectedProduct) return;
    const qty = parseInt(document.getElementById('product-detail-quantity-input').value, 10);
    addToCart(state.selectedProduct.id, qty);
    showView('catalog-view');
  });

  // Cart Controls
  document.getElementById('purchase-btn').addEventListener('click', handleCheckout);

  // Auth Modal Controls
  document.getElementById('auth-modal-close').addEventListener('click', closeAuthModal);
  document.getElementById('auth-switch-mode-btn').addEventListener('click', () => {
    openAuthModal(state.authMode === 'login' ? 'register' : 'login');
  });
  document.getElementById('auth-form').addEventListener('submit', handleAuthSubmit);
}

// Application Startup
async function initApp() {
  initEventListeners();

  // Check existing session
  try {
    state.currentUser = await apiRequest('/api/auth/me');
  } catch {
    state.currentUser = null;
  }

  updateAuthUI();
  await loadCart();
  await loadProducts();
}

window.addEventListener('DOMContentLoaded', initApp);
