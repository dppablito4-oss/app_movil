// ==============================================================================
// COPIADORA GRAFIPLOT - BUNDLED SPA (app.js)
// ==============================================================================

// --- config.js ---
// ==============================================================================
// CONFIGURACIÓN DE SUPABASE - COPIADORA GRAFIPLOT
// ==============================================================================

// Credenciales por defecto de Supabase (Copiadora Grafiplot)
const DEFAULT_SUPABASE_URL = "https://kahdnjjzzvliklxwlpse.supabase.co";
const DEFAULT_SUPABASE_ANON_KEY = "sb_publishable_88GyJuBlUZeIT5YlTbma1w_994h7mGd";

// Claves en localStorage
const STORAGE_KEY_URL = "grafiplot_supabase_url";
const STORAGE_KEY_KEY = "grafiplot_supabase_key";

function getSupabaseCredentials() {
  const customUrl = localStorage.getItem(STORAGE_KEY_URL);
  const customKey = localStorage.getItem(STORAGE_KEY_KEY);

  return {
    url: (customUrl && customUrl.trim()) ? customUrl.trim() : DEFAULT_SUPABASE_URL,
    anonKey: (customKey && customKey.trim()) ? customKey.trim() : DEFAULT_SUPABASE_ANON_KEY
  };
}

function saveSupabaseCredentials(url, anonKey) {
  if (url) localStorage.setItem(STORAGE_KEY_URL, url.trim());
  if (anonKey) localStorage.setItem(STORAGE_KEY_KEY, anonKey.trim());
}

function isSupabaseConfigured() {
  const creds = getSupabaseCredentials();
  return creds.url && !creds.url.includes("your-supabase-project") && creds.anonKey && !creds.anonKey.includes("your-supabase-anon-key");
}

// --- supabaseClient.js ---
// ==============================================================================
// CLIENTE SUPABASE - INITIALIZER
// ==============================================================================



let _supabaseClient = null;

function initSupabase() {
  const { url, anonKey } = getSupabaseCredentials();

  if (window.supabase && url && anonKey) {
    try {
      _supabaseClient = window.supabase.createClient(url, anonKey, {
        auth: {
          persistSession: true,
          autoRefreshToken: true
        }
      });
      return _supabaseClient;
    } catch (err) {
      console.error("Error al inicializar cliente Supabase:", err);
      return null;
    }
  }
  return null;
}

function getSupabase() {
  if (!_supabaseClient) {
    return initSupabase();
  }
  return _supabaseClient;
}

// --- app.js ---
// ==============================================================================
// COPIADORA GRAFIPLOT - SPA LOGIC (js/app.js)
// ==============================================================================


const state = {
  currentView: 'public-home',
  currentToken: null,
  activeFilter: 'all',
  searchQuery: '',
  jobs: [],
  tokens: [],
  customers: [],
  scanner: null,
  isScanning: false,
  isStartingScanner: false,
  isDemoMode: false,
  isAuthenticated: false,
  user: null
};

const STORAGE_JOBS = 'grafiplot_demo_jobs';
const STORAGE_TOKENS = 'grafiplot_demo_tokens';

function startApp() {
  initIcons();
  initTheme();
  setupNavigation();
  setupEventListeners();
  initDemoStore();
  handleInitialRoute();
  initAuthSession();
  checkSupabaseConnection();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', startApp);
} else {
  startApp();
}

window.addEventListener('load', () => {
  initIcons();
});

function initTheme() {
  const savedTheme = localStorage.getItem('grafiplot_theme');
  const prefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
  const initialTheme = savedTheme || (prefersLight ? 'light' : 'dark');
  applyTheme(initialTheme);

  document.getElementById('theme-toggle-btn')?.addEventListener('click', () => {
    const isLight = document.body.classList.contains('light-theme');
    const newTheme = isLight ? 'dark' : 'light';
    applyTheme(newTheme);
    localStorage.setItem('grafiplot_theme', newTheme);
  });
}

function applyTheme(theme) {
  const isLight = theme === 'light';
  document.body.classList.toggle('light-theme', isLight);
  const themeBtn = document.getElementById('theme-toggle-btn');
  if (themeBtn) {
    themeBtn.innerHTML = isLight ? '<i data-lucide="moon"></i>' : '<i data-lucide="sun"></i>';
  }
  initIcons();
}

function initIcons() {
  if (window.lucide && typeof window.lucide.createIcons === 'function') {
    try {
      window.lucide.createIcons();
    } catch (e) {
      console.warn('Lucide icons error:', e);
    }
  } else {
    setTimeout(() => {
      if (window.lucide && typeof window.lucide.createIcons === 'function') {
        try { window.lucide.createIcons(); } catch (e) {}
      }
    }, 150);
  }
}

// ------------------------------------------------------------------------------
// AUTHENTICATION MANAGEMENT
// ------------------------------------------------------------------------------
async function initAuthSession() {
  const supabase = getSupabase();
  if (supabase && supabase.auth) {
    try {
      const { data } = await supabase.auth.getSession();
      state.isAuthenticated = !!data?.session?.user;
      state.user = data?.session?.user || null;
    } catch (e) {
      state.isAuthenticated = false;
      state.user = null;
    }

    try {
      supabase.auth.onAuthStateChange((_event, session) => {
        state.isAuthenticated = !!session?.user;
        state.user = session?.user || null;
        updateAuthUI();
      });
    } catch (e) {}
  } else {
    state.isAuthenticated = false;
    state.user = null;
  }
  updateAuthUI();
}

function updateAuthUI() {
  const adminNav = document.getElementById('admin-nav-bar');
  const adminHeaderGroup = document.getElementById('admin-header-group');

  if (state.isAuthenticated) {
    if (adminNav) adminNav.classList.remove('hidden');
    if (adminHeaderGroup) adminHeaderGroup.classList.remove('hidden');
  } else {
    if (adminNav) adminNav.classList.add('hidden');
    if (adminHeaderGroup) adminHeaderGroup.classList.add('hidden');
  }
  initIcons();
}

async function handleLoginSubmit(e) {
  e.preventDefault();
  const emailInput = document.getElementById('login-email');
  const passwordInput = document.getElementById('login-password');
  const errorBox = document.getElementById('login-error-box');
  const submitBtn = document.getElementById('login-submit-btn');

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (errorBox) errorBox.classList.add('hidden');
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.innerHTML = 'Verificando credenciales...';
  }

  const supabase = getSupabase();
  if (!supabase) {
    if (errorBox) {
      errorBox.textContent = 'No se pudo conectar a Supabase.';
      errorBox.classList.remove('hidden');
    }
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.innerHTML = '<i data-lucide="log-in"></i> Iniciar Sesión';
      initIcons();
    }
    return;
  }

  try {
    const { data, error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) throw error;

    state.isAuthenticated = true;
    state.user = data.user;
    updateAuthUI();

    emailInput.value = '';
    passwordInput.value = '';
    window.location.hash = 'jobs';
    await switchView('jobs');
  } catch (err) {
    if (errorBox) {
      errorBox.textContent = err.message || 'Credenciales inválidas. Revisa tu correo y contraseña.';
      errorBox.classList.remove('hidden');
    }
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.innerHTML = '<i data-lucide="log-in"></i> Iniciar Sesión';
      initIcons();
    }
  }
}

async function handleLogout() {
  const supabase = getSupabase();
  if (supabase && supabase.auth) {
    await supabase.auth.signOut();
  }
  state.isAuthenticated = false;
  state.user = null;
  updateAuthUI();
  window.location.hash = '';
  await switchView('public-home');
}

// ------------------------------------------------------------------------------
// ROUTING & NAVIGATION
// ------------------------------------------------------------------------------
async function handleInitialRoute() {
  const urlParams = new URLSearchParams(window.location.search);
  const tokenFromUrl = urlParams.get('t') || sessionStorage.getItem('redirect_t');

  if (tokenFromUrl) {
    sessionStorage.removeItem('redirect_t');
    await openTokenView(tokenFromUrl.toUpperCase().trim());
    return;
  }

  const hash = window.location.hash.replace('#', '').replace(/^\//, '').trim();
  const path = window.location.pathname.replace(/^\/app_movil/, '').replace(/^\//, '').trim();

  if (hash === 'login' || path === 'login') {
    await switchView('login');
  } else if (hash.startsWith('t/')) {
    const t = hash.substring(2).trim();
    if (t) await openTokenView(t.toUpperCase());
  } else if (hash && ['jobs', 'scanner', 'qr-generator', 'customers'].includes(hash)) {
    if (state.isAuthenticated) {
      await switchView(hash);
    } else {
      await switchView('login');
    }
  } else {
    if (state.isAuthenticated) {
      await switchView('jobs');
    } else {
      await switchView('public-home');
    }
  }
}

function setupNavigation() {
  document.querySelectorAll('.app-nav .nav-item').forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const targetView = item.getAttribute('data-view');
      window.location.hash = targetView;
    });
  });

  window.addEventListener('hashchange', async () => {
    const hash = window.location.hash.replace('#', '').trim();
    if (hash === 'login') {
      await switchView('login');
    } else if (hash && ['jobs', 'scanner', 'qr-generator', 'customers'].includes(hash)) {
      if (state.isAuthenticated) {
        await switchView(hash);
      } else {
        await switchView('login');
      }
    } else if (hash.startsWith('t/')) {
      const t = hash.substring(2).trim();
      if (t) await openTokenView(t.toUpperCase());
    } else if (!hash) {
      if (state.isAuthenticated) {
        await switchView('jobs');
      } else {
        await switchView('public-home');
      }
    }
  });

  const logoLink = document.getElementById('logo-btn') || document.getElementById('brand-logo-link');
  logoLink?.addEventListener('click', (e) => {
    e.preventDefault();
    if (state.isAuthenticated) {
      window.location.hash = 'jobs';
      switchView('jobs');
    } else {
      window.location.hash = '';
      switchView('public-home');
    }
  });
}

async function switchView(viewName) {
  if (state.currentView === viewName && viewName === 'scanner' && state.isScanning) {
    return;
  }

  const previousView = state.currentView;
  state.currentView = viewName;

  if (previousView === 'scanner' && viewName !== 'scanner') {
    await stopQRScanner();
  }

  document.querySelectorAll('.app-view').forEach(view => view.classList.add('hidden'));
  document.querySelectorAll('.app-nav .nav-item').forEach(item => {
    item.classList.toggle('active', item.getAttribute('data-view') === viewName);
  });

  const targetViewElem = document.getElementById(`view-${viewName}`);
  if (targetViewElem) {
    targetViewElem.classList.remove('hidden');
  }

  if (viewName === 'jobs' && state.isAuthenticated) loadJobs();
  if (viewName === 'scanner' && state.isAuthenticated) startQRScanner();
  if (viewName === 'qr-generator' && state.isAuthenticated) loadQRGeneratorStudio();
  if (viewName === 'customers' && state.isAuthenticated) loadCustomers();

  initIcons();
}

// ------------------------------------------------------------------------------
// CONNECTION & DEMO STORE
// ------------------------------------------------------------------------------
async function checkSupabaseConnection() {
  const statusBtn = document.getElementById('supabase-status-btn');
  const statusText = document.getElementById('supabase-status-text');

  if (!isSupabaseConfigured()) {
    state.isDemoMode = true;
    if (statusBtn && statusText) {
      statusBtn.className = 'status-pill status-demo';
      statusText.textContent = 'Modo Demo';
    }
    return;
  }

  const supabase = getSupabase();
  if (!supabase) {
    state.isDemoMode = true;
    if (statusBtn && statusText) {
      statusBtn.className = 'status-pill status-demo';
      statusText.textContent = 'Modo Demo';
    }
    return;
  }

  state.isDemoMode = false;
  if (statusBtn && statusText) {
    statusBtn.className = 'status-pill status-online';
    statusText.textContent = 'Supabase Conectado';
  }
}

function initDemoStore() {
  if (!localStorage.getItem(STORAGE_TOKENS)) {
    const defaultTokens = [
      { id: '11111111-1111-1111-1111-111111111111', token: '7KF2PX8A', status: 'assigned', job_id: 'job-demo-1', created_at: new Date().toISOString() },
      { id: '22222222-2222-2222-2222-222222222222', token: 'M93KLP21', status: 'unused', job_id: null, created_at: new Date().toISOString() },
      { id: '33333333-3333-3333-3333-333333333333', token: 'A84XRT90', status: 'unused', job_id: null, created_at: new Date().toISOString() }
    ];
    localStorage.setItem(STORAGE_TOKENS, JSON.stringify(defaultTokens));
  }

  if (!localStorage.getItem(STORAGE_JOBS)) {
    const defaultJobs = [
      {
        id: 'job-demo-1',
        customer_name_snapshot: 'Carlos Mendoza',
        customer_phone_snapshot: '987654321',
        description: 'Impresión de planos A1 + Engargolado de tesis',
        status: 'in_progress',
        total: 45.00,
        notes: 'Papel bond 90g, anillado color negro con tapa transparente.',
        created_at: new Date(Date.now() - 3600000 * 4).toISOString(),
        token_code: '7KF2PX8A',
        items: [
          { id: 'item-1', label: 'Impresión Plano A1 Bond 90g', quantity: 3, unit_price: 10.00, subtotal: 30.00 },
          { id: 'item-2', label: 'Engargolado Tesis Espiral', quantity: 1, unit_price: 15.00, subtotal: 15.00 }
        ]
      }
    ];
    localStorage.setItem(STORAGE_JOBS, JSON.stringify(defaultJobs));
  }
}

// ------------------------------------------------------------------------------
// EVENT LISTENERS SETUP
// ------------------------------------------------------------------------------
function setupEventListeners() {
  document.getElementById('login-form')?.addEventListener('submit', handleLoginSubmit);
  document.getElementById('logout-btn')?.addEventListener('click', handleLogout);

  const publicBtn = document.getElementById('public-code-btn');
  const publicInput = document.getElementById('public-code-input');
  if (publicBtn && publicInput) {
    const doSearch = () => {
      const code = publicInput.value.toUpperCase().trim();
      if (code) openTokenView(code);
    };
    publicBtn.addEventListener('click', doSearch);
    publicInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') doSearch();
    });
  }

  const openConfigBtn = document.getElementById('open-config-btn');
  const configForm = document.getElementById('config-form');
  const useDemoBtn = document.getElementById('use-demo-mode-btn');

  if (openConfigBtn) {
    openConfigBtn.addEventListener('click', () => {
      const creds = getSupabaseCredentials();
      document.getElementById('cfg-url').value = creds.url && !creds.url.includes('your-supabase-project') ? creds.url : '';
      document.getElementById('cfg-key').value = creds.anonKey && !creds.anonKey.includes('your-supabase-anon-key') ? creds.anonKey : '';
      openModal('config-modal');
    });
  }

  if (configForm) {
    configForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const url = document.getElementById('cfg-url').value.trim();
      const key = document.getElementById('cfg-key').value.trim();
      saveSupabaseCredentials(url, key);
      initSupabase();
      checkSupabaseConnection();
      closeModal('config-modal');
      alert('Configuración guardada.');
      if (state.isAuthenticated) loadJobs();
    });
  }

  if (useDemoBtn) {
    useDemoBtn.addEventListener('click', () => {
      saveSupabaseCredentials('', '');
      checkSupabaseConnection();
      closeModal('config-modal');
      if (state.isAuthenticated) loadJobs();
    });
  }

  document.querySelectorAll('.close-modal-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const modal = e.target.closest('.modal-overlay');
      if (modal) modal.classList.add('hidden');
    });
  });

  const searchInput = document.getElementById('jobs-search-input');
  const clearSearchBtn = document.getElementById('clear-search-btn');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value.toLowerCase().trim();
      if (clearSearchBtn) clearSearchBtn.classList.toggle('hidden', state.searchQuery === '');
      renderJobsGrid();
    });
  }

  if (clearSearchBtn) {
    clearSearchBtn.addEventListener('click', () => {
      if (searchInput) searchInput.value = '';
      state.searchQuery = '';
      clearSearchBtn.classList.add('hidden');
      renderJobsGrid();
    });
  }

  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      state.activeFilter = tab.getAttribute('data-filter');
      renderJobsGrid();
    });
  });

  document.getElementById('new-job-manual-btn')?.addEventListener('click', () => openJobModal());
  document.getElementById('job-form')?.addEventListener('submit', handleJobFormSubmit);
  document.getElementById('add-item-row-btn')?.addEventListener('click', () => addItemRow());

  const goManualTokenBtn = document.getElementById('go-manual-token-btn');
  const manualTokenInput = document.getElementById('manual-token-code');
  if (goManualTokenBtn && manualTokenInput) {
    goManualTokenBtn.addEventListener('click', () => {
      const token = manualTokenInput.value.toUpperCase().trim();
      if (token) openTokenView(token);
    });
  }

  document.getElementById('back-to-jobs-btn')?.addEventListener('click', () => {
    if (state.isAuthenticated) {
      window.location.hash = 'jobs';
      switchView('jobs');
    } else {
      window.location.hash = '';
      switchView('public-home');
    }
  });

  document.getElementById('generate-batch-btn')?.addEventListener('click', handleGenerateBatchQR);
  document.getElementById('print-sheet-btn')?.addEventListener('click', () => window.print());

  document.getElementById('customer-search-input')?.addEventListener('input', (e) => {
    renderCustomersGrid(e.target.value.toLowerCase().trim());
  });
}

function openModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) {
    modal.classList.remove('hidden');
    initIcons();
  }
}

function closeModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) modal.classList.add('hidden');
}

function generateRandom8CharToken() {
  const chars = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
  let token = '';
  for (let i = 0; i < 8; i++) {
    token += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return token;
}

// ------------------------------------------------------------------------------
// TOKEN VIEW: CLIENT PUBLIC TRACKING vs ADMIN MANAGEMENT
// ------------------------------------------------------------------------------
async function openTokenView(tokenCode) {
  await stopQRScanner();
  state.currentToken = tokenCode;
  document.getElementById('token-code-display').textContent = tokenCode;

  const backBtnLabel = document.getElementById('back-btn-label');
  if (backBtnLabel) {
    backBtnLabel.textContent = state.isAuthenticated ? 'Volver a Trabajos' : 'Nueva Consulta';
  }

  await switchView('token');

  const contentArea = document.getElementById('token-content-area');
  contentArea.innerHTML = `<div class="loading-spinner"><div class="spinner"></div><p>Cargando información del pedido ${tokenCode}...</p></div>`;

  let tokenRecord = null;
  let jobRecord = null;

  if (state.isDemoMode) {
    const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
    tokenRecord = tokens.find(t => t.token === tokenCode);

    if (tokenRecord && tokenRecord.job_id) {
      const jobs = JSON.parse(localStorage.getItem(STORAGE_JOBS) || '[]');
      jobRecord = jobs.find(j => j.id === tokenRecord.job_id);
    }
  } else {
    const supabase = getSupabase();
    try {
      const { data: res } = await supabase.rpc('get_public_job_by_token', { p_token: tokenCode });
      if (res) {
        if (res.status === 'not_found') {
          tokenRecord = null;
        } else if (res.status === 'unused') {
          tokenRecord = { token: tokenCode, status: 'unused', job_id: null };
        } else if (res.status === 'assigned' && res.job) {
          tokenRecord = { token: tokenCode, status: 'assigned', job_id: res.job.id || 'remote-job' };
          jobRecord = {
            id: res.job.id || 'remote-job',
            customer_name_snapshot: res.job.customer_name,
            customer_phone_snapshot: res.job.customer_phone,
            description: res.job.description,
            status: res.job.job_status,
            total: (res.job.total_cents || 0) / 100.0,
            created_at: res.job.created_at,
            ready_at: res.job.ready_at,
            delivered_at: res.job.delivered_at,
            items: (res.items || []).map(i => ({
              label: i.description,
              quantity: i.quantity,
              unit_price: (i.unit_price_cents || 0) / 100.0,
              subtotal: (i.subtotal_cents || 0) / 100.0
            }))
          };
        }
      }
    } catch(err) {
      console.warn('Error RPC get_public_job_by_token:', err);
    }
  }

  // BRANCH A: PUBLIC CLIENT VIEW
  if (!state.isAuthenticated) {
    if (!tokenRecord || tokenRecord.status !== 'assigned' || !jobRecord) {
      contentArea.innerHTML = `
        <div class="client-invalid-card">
          <div class="client-invalid-icon"><i data-lucide="alert-octagon"></i></div>
          <h2>Código QR No Válido</h2>
          <p class="text-muted mt-2">
            El código <strong>${escapeHtml(tokenCode)}</strong> no corresponde a ningún pedido activo en Copiadora Grafiplot.
          </p>
          <div class="mt-4">
            <button id="client-back-search-btn" class="btn btn-primary">
              <i data-lucide="search"></i> Consultar Otro Código
            </button>
          </div>
        </div>
      `;
      document.getElementById('client-back-search-btn')?.addEventListener('click', () => switchView('public-home'));
      initIcons();
      return;
    }

    renderPublicCustomerTracking(contentArea, jobRecord, tokenCode);
    initIcons();
    return;
  }

  // BRANCH B: ADMIN / OPERATOR VIEW
  if (!tokenRecord) {
    contentArea.innerHTML = `
      <div class="token-banner banner-warning">
        <div class="banner-icon"><i data-lucide="alert-triangle"></i></div>
        <div class="banner-body">
          <h3>Código QR no registrado (${tokenCode})</h3>
          <p>Este código QR no existe aún en la base de datos de la copiadora.</p>
          <div class="banner-actions mt-3">
            <button id="register-token-now-btn" class="btn btn-primary">
              <i data-lucide="plus-circle"></i> Registrar este QR como Disponible
            </button>
          </div>
        </div>
      </div>
    `;

    document.getElementById('register-token-now-btn')?.addEventListener('click', async () => {
      await registerNewToken(tokenCode);
      openTokenView(tokenCode);
    });
    initIcons();
    return;
  }

  if (tokenRecord.status === 'unused' || !tokenRecord.job_id) {
    contentArea.innerHTML = `
      <div class="token-banner banner-available">
        <div class="banner-icon"><i data-lucide="check-circle-2"></i></div>
        <div class="banner-body">
          <span class="badge badge-ready">QR DISPONIBLE</span>
          <h3>El código ${tokenCode} está listo para ser asignado</h3>
          <p>Puedes vincular esta etiqueta física a un nuevo trabajo o asignarla a un trabajo que ya existe.</p>
          
          <div class="banner-actions mt-3 flex-wrap gap-2">
            <button id="assign-new-job-btn" class="btn btn-emerald btn-lg">
              <i data-lucide="file-plus"></i> Crear Nuevo Trabajo con este QR
            </button>
            <button id="assign-existing-job-btn" class="btn btn-secondary btn-lg">
              <i data-lucide="link"></i> Asignar a Trabajo Existente
            </button>
          </div>
        </div>
      </div>
    `;

    document.getElementById('assign-new-job-btn')?.addEventListener('click', () => {
      openJobModal(null, tokenCode);
    });

    document.getElementById('assign-existing-job-btn')?.addEventListener('click', () => {
      promptAssignToExistingJob(tokenCode);
    });

    initIcons();
    return;
  }

  if (jobRecord) {
    renderAdminJobDetailCard(contentArea, jobRecord, tokenCode);
  } else {
    contentArea.innerHTML = `
      <div class="alert-info">
        El token ${tokenCode} está marcado como asignado pero el trabajo original fue removido.
        <button id="reset-orphan-token-btn" class="btn btn-sm btn-secondary mt-2">Liberar QR</button>
      </div>
    `;
    document.getElementById('reset-orphan-token-btn')?.addEventListener('click', () => releaseQRToken(tokenCode));
  }

  initIcons();
}

function renderPublicCustomerTracking(container, job, tokenCode) {
  const statusConfig = {
    received: {
      title: '¡Trabajo Recibido!',
      desc: 'Tu orden fue ingresada y se encuentra en cola para ser atendida.',
      cssClass: 'status-received',
      icon: 'clock'
    },
    in_progress: {
      title: 'En Proceso de Impresión / Copiado',
      desc: 'Nuestro equipo se encuentra procesando tu pedido en este momento.',
      cssClass: 'status-in_progress',
      icon: 'printer'
    },
    ready: {
      title: '¡LISTO PARA RECOGER!',
      desc: 'Tu trabajo está 100% terminado. Puedes acercarte a nuestro local a recogerlo.',
      cssClass: 'status-ready',
      icon: 'package-check'
    },
    delivered: {
      title: 'Trabajo Entregado',
      desc: 'Este trabajo ya fue entregado y liquidado con éxito. ¡Gracias por tu preferencia!',
      cssClass: 'status-delivered',
      icon: 'check-circle-2'
    }
  };

  const cfg = statusConfig[job.status] || statusConfig.received;
  const items = job.items || job.job_items || [];
  const itemsHtml = items.map(item => `
    <tr>
      <td>${escapeHtml(item.label)}</td>
      <td class="text-center">${item.quantity}</td>
      <td class="text-right">S/ ${parseFloat(item.unit_price).toFixed(2)}</td>
      <td class="text-right font-semibold">S/ ${parseFloat(item.subtotal).toFixed(2)}</td>
    </tr>
  `).join('');

  container.innerHTML = `
    <div class="client-tracking-container">
      <div class="client-status-hero ${cfg.cssClass}">
        <i data-lucide="${cfg.icon}" style="font-size: 2.8rem; width: 44px; height: 44px;"></i>
        <div class="client-status-title">${cfg.title}</div>
        <p class="client-status-desc">${cfg.desc}</p>
      </div>

      <div class="workflow-stepper">
        <div class="step ${['received', 'in_progress', 'ready', 'delivered'].includes(job.status) ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="check"></i></div>
          <span class="step-label">RECIBIDO</span>
        </div>
        <div class="step-line ${['in_progress', 'ready', 'delivered'].includes(job.status) ? 'active' : ''}"></div>

        <div class="step ${['in_progress', 'ready', 'delivered'].includes(job.status) ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="printer"></i></div>
          <span class="step-label">EN PROCESO</span>
        </div>
        <div class="step-line ${['ready', 'delivered'].includes(job.status) ? 'active' : ''}"></div>

        <div class="step ${['ready', 'delivered'].includes(job.status) ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="package-check"></i></div>
          <span class="step-label">LISTO</span>
        </div>
        <div class="step-line ${job.status === 'delivered' ? 'active' : ''}"></div>

        <div class="step ${job.status === 'delivered' ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="user-check"></i></div>
          <span class="step-label">ENTREGADO</span>
        </div>
      </div>

      <div class="job-card-detailed mt-4">
        <div class="job-card-header">
          <div>
            <span class="text-muted">Cliente:</span>
            <h2>${escapeHtml(job.customer_name_snapshot)}</h2>
          </div>
          <div class="job-total-badge">
            <span class="total-label">TOTAL A PAGAR</span>
            <span class="total-amount">S/ ${parseFloat(job.total).toFixed(2)}</span>
          </div>
        </div>

        <div class="job-items-section mt-3">
          <h4>Detalle del Servicio</h4>
          <table class="table-items">
            <thead>
              <tr>
                <th>Descripción</th>
                <th class="text-center">Cant.</th>
                <th class="text-right">P. Unit</th>
                <th class="text-right">Subtotal</th>
              </tr>
            </thead>
            <tbody>
              ${itemsHtml || '<tr><td colspan="4">Sin desglose especificado</td></tr>'}
            </tbody>
          </table>
        </div>

        ${job.notes ? `
          <div class="job-notes-box mt-3">
            <strong>Indicaciones:</strong> ${escapeHtml(job.notes)}
          </div>
        ` : ''}

        <div class="text-center text-muted mt-4 font-semibold" style="font-size: 0.85rem;">
          Copiadora Grafiplot &bull; Atención de Calidad
        </div>
      </div>
    </div>
  `;
}

function renderAdminJobDetailCard(container, job, tokenCode) {
  const statusLabels = {
    received: 'RECIBIDO (Pendiente)',
    in_progress: 'EN PROCESO',
    ready: 'LISTO PARA RECOGER',
    delivered: 'ENTREGADO',
    cancelled: 'CANCELADO'
  };

  const statusClasses = {
    received: 'badge-received',
    in_progress: 'badge-progress',
    ready: 'badge-ready',
    delivered: 'badge-delivered',
    cancelled: 'badge-cancelled'
  };

  const items = job.items || job.job_items || [];
  const itemsHtml = items.map(item => `
    <tr>
      <td>${escapeHtml(item.label)}</td>
      <td class="text-center">${item.quantity}</td>
      <td class="text-right">S/ ${parseFloat(item.unit_price).toFixed(2)}</td>
      <td class="text-right font-semibold">S/ ${parseFloat(item.subtotal).toFixed(2)}</td>
    </tr>
  `).join('');

  container.innerHTML = `
    <div class="job-card-detailed">
      <div class="job-card-header">
        <div>
          <span class="badge ${statusClasses[job.status] || ''}">
            ${statusLabels[job.status] || job.status}
          </span>
          <h2 class="mt-1">${escapeHtml(job.customer_name_snapshot)}</h2>
          <p class="text-muted"><i data-lucide="phone" class="icon-inline"></i> ${escapeHtml(job.customer_phone_snapshot || 'Sin teléfono')}</p>
        </div>

        <div class="job-total-badge">
          <span class="total-label">TOTAL</span>
          <span class="total-amount">S/ ${parseFloat(job.total).toFixed(2)}</span>
        </div>
      </div>

      <div class="workflow-stepper">
        <div class="step ${['received', 'in_progress', 'ready', 'delivered'].includes(job.status) ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="check"></i></div>
          <span class="step-label">RECIBIDO</span>
        </div>
        <div class="step-line ${['in_progress', 'ready', 'delivered'].includes(job.status) ? 'active' : ''}"></div>

        <div class="step ${['in_progress', 'ready', 'delivered'].includes(job.status) ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="printer"></i></div>
          <span class="step-label">EN PROCESO</span>
        </div>
        <div class="step-line ${['ready', 'delivered'].includes(job.status) ? 'active' : ''}"></div>

        <div class="step ${['ready', 'delivered'].includes(job.status) ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="package-check"></i></div>
          <span class="step-label">LISTO</span>
        </div>
        <div class="step-line ${job.status === 'delivered' ? 'active' : ''}"></div>

        <div class="step ${job.status === 'delivered' ? 'completed' : ''}">
          <div class="step-dot"><i data-lucide="user-check"></i></div>
          <span class="step-label">ENTREGADO</span>
        </div>
      </div>

      <div class="action-buttons-group mt-3">
        <h4>Actualizar Estado del Trabajo:</h4>
        <div class="btn-group-responsive">
          <button class="btn btn-sm btn-status-change ${job.status === 'received' ? 'active-status' : ''}" data-newstatus="received">
            Recibido
          </button>
          <button class="btn btn-sm btn-status-change ${job.status === 'in_progress' ? 'active-status' : ''}" data-newstatus="in_progress">
            En Proceso
          </button>
          <button class="btn btn-sm btn-emerald btn-status-change ${job.status === 'ready' ? 'active-status' : ''}" data-newstatus="ready">
            <i data-lucide="check-circle"></i> ¡LISTO!
          </button>
          <button class="btn btn-sm btn-indigo btn-status-change ${job.status === 'delivered' ? 'active-status' : ''}" data-newstatus="delivered">
            Entregado
          </button>
        </div>
      </div>

      <div class="job-items-section mt-3">
        <h4>Detalle de Servicios</h4>
        <table class="table-items">
          <thead>
            <tr>
              <th>Descripción</th>
              <th class="text-center">Cant.</th>
              <th class="text-right">P. Unit</th>
              <th class="text-right">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            ${itemsHtml || '<tr><td colspan="4">Sin ítems desglosados</td></tr>'}
          </tbody>
        </table>
      </div>

      ${job.notes ? `
        <div class="job-notes-box mt-3">
          <strong><i data-lucide="file-text" class="icon-inline"></i> Notas:</strong> ${escapeHtml(job.notes)}
        </div>
      ` : ''}

      <div class="card-footer-actions mt-4">
        <button id="open-wa-modal-btn" class="btn btn-emerald">
          <i data-lucide="message-square"></i> Notificar por WhatsApp
        </button>

        <button id="release-qr-btn" class="btn btn-danger-outline">
          <i data-lucide="unlink"></i> Liberar Código QR (${tokenCode})
        </button>
      </div>
    </div>
  `;

  container.querySelectorAll('.btn-status-change').forEach(btn => {
    btn.addEventListener('click', async () => {
      const newStatus = btn.getAttribute('data-newstatus');
      await updateJobStatus(job.id, newStatus);
      openTokenView(tokenCode);
    });
  });

  container.querySelector('#open-wa-modal-btn')?.addEventListener('click', () => {
    openWhatsAppModal(job, tokenCode);
  });

  container.querySelector('#release-qr-btn')?.addEventListener('click', async () => {
    if (confirm(`¿Estás seguro de liberar el código QR ${tokenCode}? Podrás usar esta etiqueta adhesiva física en otro trabajo.`)) {
      await releaseQRToken(tokenCode);
      openTokenView(tokenCode);
    }
  });
}

async function registerNewToken(tokenCode) {
  if (state.isDemoMode) {
    const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
    tokens.push({ id: crypto.randomUUID(), token: tokenCode, status: 'unused', job_id: null, created_at: new Date().toISOString() });
    localStorage.setItem(STORAGE_TOKENS, JSON.stringify(tokens));
  } else {
    const supabase = getSupabase();
    await supabase.from('qr_tokens').insert({ token: tokenCode, status: 'unused' });
  }
}

async function updateJobStatus(jobId, newStatus) {
  const timestamps = {};
  if (newStatus === 'ready') timestamps.ready_at = new Date().toISOString();
  if (newStatus === 'delivered') timestamps.delivered_at = new Date().toISOString();

  if (state.isDemoMode) {
    const jobs = JSON.parse(localStorage.getItem(STORAGE_JOBS) || '[]');
    const index = jobs.findIndex(j => j.id === jobId);
    if (index !== -1) {
      jobs[index].status = newStatus;
      Object.assign(jobs[index], timestamps);
      localStorage.setItem(STORAGE_JOBS, JSON.stringify(jobs));
    }
  } else {
    const supabase = getSupabase();
    await supabase.from('jobs').update({ status: newStatus, ...timestamps, updated_at: new Date().toISOString() }).eq('id', jobId);
  }
}

async function releaseQRToken(tokenCode) {
  if (state.isDemoMode) {
    const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
    const index = tokens.findIndex(t => t.token === tokenCode);
    if (index !== -1) {
      tokens[index].status = 'unused';
      tokens[index].job_id = null;
      localStorage.setItem(STORAGE_TOKENS, JSON.stringify(tokens));
    }
  } else {
    const supabase = getSupabase();
    await supabase.from('qr_tokens').update({ status: 'unused', job_id: null, released_at: new Date().toISOString() }).eq('token', tokenCode);
  }
}

async function promptAssignToExistingJob(tokenCode) {
  let jobs = [];
  if (state.isDemoMode) {
    jobs = JSON.parse(localStorage.getItem(STORAGE_JOBS) || '[]');
  } else {
    const supabase = getSupabase();
    const { data } = await supabase.from('jobs').select('*').order('created_at', { ascending: false }).limit(10);
    jobs = data || [];
  }

  if (jobs.length === 0) {
    alert('No hay trabajos creados aún. Crea un nuevo trabajo primero.');
    openJobModal(null, tokenCode);
    return;
  }

  const options = jobs.map((j, i) => `${i + 1}. ${j.customer_name_snapshot} (S/ ${parseFloat(j.total).toFixed(2)})`).join('
');
  const choice = prompt(`Selecciona el número del trabajo para asignar el QR ${tokenCode}:

${options}`);

  if (choice) {
    const index = parseInt(choice, 10) - 1;
    if (jobs[index]) {
      const targetJob = jobs[index];
      if (state.isDemoMode) {
        const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
        const tIndex = tokens.findIndex(t => t.token === tokenCode);
        if (tIndex !== -1) {
          tokens[tIndex].status = 'assigned';
          tokens[tIndex].job_id = targetJob.id;
          localStorage.setItem(STORAGE_TOKENS, JSON.stringify(tokens));
        }
      } else {
        const supabase = getSupabase();
        await supabase.from('qr_tokens').update({ status: 'assigned', job_id: targetJob.id, assigned_at: new Date().toISOString() }).eq('token', tokenCode);
      }
      openTokenView(tokenCode);
    }
  }
}

// ------------------------------------------------------------------------------
// JOBS CENTER
// ------------------------------------------------------------------------------
async function loadJobs() {
  const jobsListElem = document.getElementById('jobs-list');
  if (jobsListElem) {
    jobsListElem.innerHTML = `<div class="loading-spinner"><div class="spinner"></div><p>Cargando lista de trabajos...</p></div>`;
  }

  if (state.isDemoMode) {
    state.jobs = JSON.parse(localStorage.getItem(STORAGE_JOBS) || '[]');
  } else {
    const supabase = getSupabase();
    const { data, error } = await supabase.from('jobs').select('*, job_items(*), qr_tokens(token)').order('created_at', { ascending: false });
    if (!error && data) {
      state.jobs = data;
    }
  }

  updateFilterCounts();
  renderJobsGrid();
}

function updateFilterCounts() {
  const counts = {
    all: state.jobs.length,
    received: state.jobs.filter(j => j.status === 'received').length,
    in_progress: state.jobs.filter(j => j.status === 'in_progress').length,
    ready: state.jobs.filter(j => j.status === 'ready').length,
    delivered: state.jobs.filter(j => j.status === 'delivered').length
  };

  for (const [key, count] of Object.entries(counts)) {
    const elem = document.getElementById(`count-${key}`);
    if (elem) elem.textContent = count;
  }
}

function renderJobsGrid() {
  const jobsListElem = document.getElementById('jobs-list');
  const emptyStateElem = document.getElementById('jobs-empty-state');
  if (!jobsListElem) return;

  let filtered = state.jobs;

  if (state.activeFilter !== 'all') {
    filtered = filtered.filter(j => j.status === state.activeFilter);
  }

  if (state.searchQuery) {
    const q = state.searchQuery;
    filtered = filtered.filter(j => {
      const name = (j.customer_name_snapshot || '').toLowerCase();
      const phone = (j.customer_phone_snapshot || '').toLowerCase();
      const desc = (j.description || '').toLowerCase();
      const token = (j.token_code || (j.qr_tokens && j.qr_tokens[0]?.token) || '').toLowerCase();
      return name.includes(q) || phone.includes(q) || desc.includes(q) || token.includes(q);
    });
  }

  if (filtered.length === 0) {
    jobsListElem.innerHTML = '';
    if (emptyStateElem) emptyStateElem.classList.remove('hidden');
    return;
  }

  if (emptyStateElem) emptyStateElem.classList.add('hidden');

  const statusLabels = {
    received: 'RECIBIDO',
    in_progress: 'EN PROCESO',
    ready: 'LISTO',
    delivered: 'ENTREGADO',
    cancelled: 'CANCELADO'
  };

  const statusClasses = {
    received: 'badge-received',
    in_progress: 'badge-progress',
    ready: 'badge-ready',
    delivered: 'badge-delivered',
    cancelled: 'badge-cancelled'
  };

  jobsListElem.innerHTML = filtered.map(job => {
    const token = job.token_code || (job.qr_tokens && job.qr_tokens[0]?.token) || null;
    const formattedDate = new Date(job.created_at).toLocaleString('es-PE', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });

    return `
      <div class="job-card" data-jobid="${job.id}">
        <div class="job-card-top">
          <span class="badge ${statusClasses[job.status] || ''}">${statusLabels[job.status] || job.status}</span>
          ${token ? `<a href="#t/${token}" class="token-chip" data-token="${token}"><i data-lucide="qr-code"></i> ${token}</a>` : '<span class="token-chip-none">Sin QR</span>'}
        </div>

        <h3 class="job-customer-name">${escapeHtml(job.customer_name_snapshot)}</h3>
        <p class="job-desc text-muted">${escapeHtml(job.description || 'Sin detalles')}</p>

        <div class="job-card-info-row">
          <span><i data-lucide="phone" class="icon-inline"></i> ${escapeHtml(job.customer_phone_snapshot || 'N/A')}</span>
          <span><i data-lucide="clock" class="icon-inline"></i> ${formattedDate}</span>
        </div>

        <div class="job-card-bottom">
          <div class="job-total-value">
            S/ ${parseFloat(job.total).toFixed(2)}
          </div>
          <button class="btn btn-secondary btn-sm view-job-detail-btn" data-jobid="${job.id}" data-token="${token || ''}">
            Ver Detalle <i data-lucide="chevron-right"></i>
          </button>
        </div>
      </div>
    `;
  }).join('');

  jobsListElem.querySelectorAll('.token-chip, .view-job-detail-btn').forEach(elem => {
    elem.addEventListener('click', (e) => {
      e.preventDefault();
      const token = elem.getAttribute('data-token');
      if (token) {
        openTokenView(token);
      } else {
        const jobId = elem.getAttribute('data-jobid');
        openJobModal(jobId);
      }
    });
  });

  initIcons();
}

// ------------------------------------------------------------------------------
// JOB MODAL & FORM
// ------------------------------------------------------------------------------
function openJobModal(jobId = null, tokenCode = null) {
  const modalTokenBanner = document.getElementById('modal-token-banner');
  const modalTokenTag = document.getElementById('modal-token-tag');
  const formTokenCode = document.getElementById('form-token-code');
  const formJobId = document.getElementById('form-job-id');

  formJobId.value = jobId || '';
  formTokenCode.value = tokenCode || '';

  if (tokenCode) {
    modalTokenBanner.classList.remove('hidden');
    modalTokenTag.textContent = tokenCode;
  } else {
    modalTokenBanner.classList.add('hidden');
  }

  const itemsContainer = document.getElementById('job-items-container');
  itemsContainer.innerHTML = '';

  if (jobId) {
    const job = state.jobs.find(j => j.id === jobId);
    if (job) {
      document.getElementById('job-customer-name').value = job.customer_name_snapshot || '';
      document.getElementById('job-customer-phone').value = job.customer_phone_snapshot || '';
      document.getElementById('job-status').value = job.status || 'received';
      document.getElementById('job-notes').value = job.notes || '';

      const items = job.items || job.job_items || [];
      if (items.length > 0) {
        items.forEach(item => addItemRow(item.label, item.quantity, item.unit_price));
      } else {
        addItemRow();
      }
    }
  } else {
    document.getElementById('job-customer-name').value = '';
    document.getElementById('job-customer-phone').value = '';
    document.getElementById('job-status').value = 'received';
    document.getElementById('job-notes').value = '';
    addItemRow('Fotocopias / Impresiones', 10, 0.20);
  }

  calculateTotal();
  openModal('job-modal');
}

function addItemRow(label = '', qty = 1, price = 0.00) {
  const container = document.getElementById('job-items-container');
  const rowId = 'item-row-' + Date.now() + Math.random().toString(36).substr(2, 4);

  const rowHtml = document.createElement('div');
  rowHtml.className = 'item-row grid-items';
  rowHtml.id = rowId;
  rowHtml.innerHTML = `
    <input type="text" class="form-control item-label" placeholder="Servicio (ej: Anillado, Copia)" value="${escapeHtml(label)}" required>
    <input type="number" class="form-control item-qty text-center" min="1" value="${qty}" required>
    <input type="number" class="form-control item-price text-right" step="0.10" min="0" value="${parseFloat(price).toFixed(2)}" required>
    <div class="item-subtotal text-right font-semibold">S/ ${(qty * price).toFixed(2)}</div>
    <button type="button" class="btn-icon text-danger remove-item-btn">&times;</button>
  `;

  container.appendChild(rowHtml);

  rowHtml.querySelectorAll('input').forEach(input => {
    input.addEventListener('input', () => {
      const q = parseFloat(rowHtml.querySelector('.item-qty').value) || 0;
      const p = parseFloat(rowHtml.querySelector('.item-price').value) || 0;
      rowHtml.querySelector('.item-subtotal').textContent = `S/ ${(q * p).toFixed(2)}`;
      calculateTotal();
    });
  });

  rowHtml.querySelector('.remove-item-btn').addEventListener('click', () => {
    rowHtml.remove();
    calculateTotal();
  });

  calculateTotal();
}

function calculateTotal() {
  let total = 0;
  document.querySelectorAll('#job-items-container .item-row').forEach(row => {
    const qty = parseFloat(row.querySelector('.item-qty').value) || 0;
    const price = parseFloat(row.querySelector('.item-price').value) || 0;
    total += (qty * price);
  });

  const totalInput = document.getElementById('job-total-display');
  if (totalInput) totalInput.value = total.toFixed(2);
}

async function handleJobFormSubmit(e) {
  e.preventDefault();

  const jobId = document.getElementById('form-job-id').value;
  const tokenCode = document.getElementById('form-token-code').value;
  const customerName = document.getElementById('job-customer-name').value.trim();
  const customerPhone = document.getElementById('job-customer-phone').value.trim();
  const status = document.getElementById('job-status').value;
  const notes = document.getElementById('job-notes').value.trim();
  const total = parseFloat(document.getElementById('job-total-display').value) || 0;

  const items = [];
  document.querySelectorAll('#job-items-container .item-row').forEach(row => {
    const label = row.querySelector('.item-label').value.trim();
    const qty = parseInt(row.querySelector('.item-qty').value, 10) || 1;
    const price = parseFloat(row.querySelector('.item-price').value) || 0;
    if (label) {
      items.push({ label, quantity: qty, unit_price: price, subtotal: qty * price });
    }
  });

  const descriptionSummary = items.map(i => `${i.quantity}x ${i.label}`).join(', ');

  const newJobObj = {
    id: jobId || ('job-' + Date.now()),
    customer_name_snapshot: customerName,
    customer_phone_snapshot: customerPhone,
    description: descriptionSummary || 'Servicios de fotocopiado',
    status: status,
    total: total,
    notes: notes,
    created_at: new Date().toISOString(),
    items: items
  };

  if (state.isDemoMode) {
    const jobs = JSON.parse(localStorage.getItem(STORAGE_JOBS) || '[]');
    if (jobId) {
      const idx = jobs.findIndex(j => j.id === jobId);
      if (idx !== -1) jobs[idx] = newJobObj;
    } else {
      jobs.unshift(newJobObj);
    }
    localStorage.setItem(STORAGE_JOBS, JSON.stringify(jobs));

    if (tokenCode) {
      const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
      const tIdx = tokens.findIndex(t => t.token === tokenCode);
      if (tIdx !== -1) {
        tokens[tIdx].status = 'assigned';
        tokens[tIdx].job_id = newJobObj.id;
      } else {
        tokens.push({ id: crypto.randomUUID(), token: tokenCode, status: 'assigned', job_id: newJobObj.id, created_at: new Date().toISOString() });
      }
      localStorage.setItem(STORAGE_TOKENS, JSON.stringify(tokens));
    }
  } else {
    const supabase = getSupabase();
    let savedJobId = jobId;

    if (jobId) {
      await supabase.from('jobs').update({
        customer_name_snapshot: customerName,
        customer_phone_snapshot: customerPhone,
        description: descriptionSummary,
        status: status,
        total_cents: Math.round(total * 100),
        notes: notes,
        updated_at: new Date().toISOString()
      }).eq('id', jobId);
    } else {
      const { data: inserted } = await supabase.from('jobs').insert({
        customer_name_snapshot: customerName,
        customer_phone_snapshot: customerPhone,
        description: descriptionSummary,
        status: status,
        total_cents: Math.round(total * 100),
        notes: notes
      }).select().single();

      if (inserted) savedJobId = inserted.id;
    }

    if (items.length > 0 && savedJobId) {
      await supabase.from('job_items').delete().eq('job_id', savedJobId);
      const itemsToInsert = items.map(i => ({
        job_id: savedJobId,
        description: i.label,
        quantity: i.quantity,
        unit_price_cents: Math.round(i.unit_price * 100),
        subtotal_cents: Math.round(i.subtotal * 100)
      }));
      await supabase.from('job_items').insert(itemsToInsert);
    }

    if (tokenCode && savedJobId) {
      await supabase.from('qr_tokens').update({ status: 'assigned', job_id: savedJobId, assigned_at: new Date().toISOString() }).eq('token', tokenCode);
    }
  }

  closeModal('job-modal');

  if (tokenCode) {
    openTokenView(tokenCode);
  } else {
    loadJobs();
  }
}

// ------------------------------------------------------------------------------
// CAMERA QR SCANNER
// ------------------------------------------------------------------------------
async function startQRScanner() {
  const readerDiv = document.getElementById('reader');
  if (!readerDiv) return;

  if (state.isStartingScanner || state.isScanning) return;
  state.isStartingScanner = true;

  await stopQRScanner();
  readerDiv.innerHTML = '';

  if (window.Html5Qrcode) {
    const html5QrCode = new window.Html5Qrcode('reader');
    state.scanner = html5QrCode;

    try {
      await html5QrCode.start(
        { facingMode: 'environment' },
        { 
          fps: 15, 
          qrbox: (viewfinderWidth, viewfinderHeight) => {
            const minEdge = Math.min(viewfinderWidth, viewfinderHeight);
            const size = Math.max(180, Math.floor(minEdge * 0.75));
            return { width: size, height: size };
          },
          aspectRatio: 1.0
        },
        async (decodedText) => {
          await stopQRScanner();
          let token = decodedText.trim();
          if (token.includes('/t/')) {
            token = token.split('/t/')[1].split('?')[0];
          } else if (token.includes('t=')) {
            token = token.split('t=')[1].split('&')[0];
          }
          openTokenView(token.toUpperCase());
        },
        (errorMessage) => {}
      );
      state.isScanning = true;
    } catch (err) {
      console.warn('No se pudo iniciar la cámara:', err);
      readerDiv.innerHTML = `<div class="alert-info">No se pudo acceder a la cámara. Por favor permite los permisos de cámara en tu navegador o usa el ingreso manual abajo.</div>`;
    } finally {
      state.isStartingScanner = false;
    }
  } else {
    state.isStartingScanner = false;
  }
}

async function stopQRScanner() {
  state.isScanning = false;
  if (state.scanner) {
    try {
      if (state.scanner.isScanning) {
        await state.scanner.stop();
      }
      await state.scanner.clear();
    } catch (e) {}
    state.scanner = null;
  }
  const readerDiv = document.getElementById('reader');
  if (readerDiv) readerDiv.innerHTML = '';
}

// ------------------------------------------------------------------------------
// QR GENERATOR & PRINT STUDIO
// ------------------------------------------------------------------------------
async function loadQRGeneratorStudio() {
  const grid = document.getElementById('printable-qr-grid');
  if (!grid) return;

  let unusedTokens = [];
  if (state.isDemoMode) {
    const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
    unusedTokens = tokens.filter(t => t.status === 'unused');
  } else {
    const supabase = getSupabase();
    const { data } = await supabase.from('qr_tokens').select('*').eq('status', 'unused').limit(24);
    unusedTokens = data || [];
  }

  if (unusedTokens.length === 0) {
    grid.innerHTML = `<div class="alert-info text-center">No hay códigos QR libres sin usar. Haz clic en "Generar Lote en BD" arriba para crear nuevos tokens imprimibles.</div>`;
    return;
  }

  renderPrintableLabels(unusedTokens);
}

async function handleGenerateBatchQR() {
  const countSelect = document.getElementById('batch-count-input');
  const count = parseInt(countSelect.value, 10) || 24;

  const newTokens = [];
  for (let i = 0; i < count; i++) {
    newTokens.push(generateRandom8CharToken());
  }

  if (state.isDemoMode) {
    const tokens = JSON.parse(localStorage.getItem(STORAGE_TOKENS) || '[]');
    newTokens.forEach(tCode => {
      tokens.push({ id: crypto.randomUUID(), token: tCode, status: 'unused', job_id: null, created_at: new Date().toISOString() });
    });
    localStorage.setItem(STORAGE_TOKENS, JSON.stringify(tokens));
  } else {
    const supabase = getSupabase();
    const insertPayload = newTokens.map(t => ({ token: t, status: 'unused' }));
    await supabase.from('qr_tokens').insert(insertPayload);
  }

  alert(`¡Se generaron con éxito ${count} nuevos códigos QR!`);
  loadQRGeneratorStudio();
}

function renderPrintableLabels(tokens) {
  const grid = document.getElementById('printable-qr-grid');
  grid.innerHTML = '';

  tokens.forEach(t => {
    const labelCard = document.createElement('div');
    labelCard.className = 'qr-label-card';

    const qrUrl = `https://qrscan.grafiplotvasquez.lat/t/${t.token}`;
    const qrImgSrc = `https://api.qrserver.com/v1/create-qr-code/?size=150x150&margin=0&data=${encodeURIComponent(qrUrl)}`;

    labelCard.innerHTML = `
      <div class="label-brand">COPIADORA GRAFIPLOT</div>
      <div class="qr-code-wrapper">
        <img class="qr-img" src="${qrImgSrc}" alt="QR ${t.token}" loading="eager" />
      </div>
      <div class="label-token-code">${t.token}</div>
      <div class="label-sub">qrscan.grafiplotvasquez.lat</div>
    `;

    grid.appendChild(labelCard);
  });
}

// ------------------------------------------------------------------------------
// CUSTOMERS DIRECTORY
// ------------------------------------------------------------------------------
async function loadCustomers() {
  if (state.isDemoMode) {
    const jobs = JSON.parse(localStorage.getItem(STORAGE_JOBS) || '[]');
    const customerMap = {};
    jobs.forEach(j => {
      const name = j.customer_name_snapshot;
      if (!customerMap[name]) {
        customerMap[name] = { name, phone: j.customer_phone_snapshot, jobsCount: 0, totalSpent: 0 };
      }
      customerMap[name].jobsCount++;
      customerMap[name].totalSpent += parseFloat(j.total) || 0;
    });
    state.customers = Object.values(customerMap);
  } else {
    const supabase = getSupabase();
    const { data } = await supabase.from('customers').select('*, jobs(id, total, total_cents)');
    if (data) {
      state.customers = data.map(c => ({
        name: c.name,
        phone: c.phone,
        jobsCount: c.jobs ? c.jobs.length : 0,
        totalSpent: c.jobs ? c.jobs.reduce((acc, j) => acc + (j.total_cents ? j.total_cents / 100.0 : parseFloat(j.total) || 0), 0) : 0
      }));
    }
  }

  renderCustomersGrid();
}

function renderCustomersGrid(filterQuery = '') {
  const container = document.getElementById('customers-list');
  if (!container) return;

  let list = state.customers;
  if (filterQuery) {
    list = list.filter(c => c.name.toLowerCase().includes(filterQuery) || (c.phone && c.phone.includes(filterQuery)));
  }

  if (list.length === 0) {
    container.innerHTML = `<div class="empty-state">No se encontraron clientes registrados.</div>`;
    return;
  }

  container.innerHTML = list.map(c => `
    <div class="customer-card">
      <div class="cust-header">
        <div class="cust-avatar">${c.name.charAt(0).toUpperCase()}</div>
        <div>
          <h3>${escapeHtml(c.name)}</h3>
          <p class="text-muted"><i data-lucide="phone" class="icon-inline"></i> ${escapeHtml(c.phone || 'Sin número')}</p>
        </div>
      </div>
      <div class="cust-stats mt-3">
        <div>
          <span class="stat-label">Trabajos:</span>
          <strong>${c.jobsCount}</strong>
        </div>
        <div>
          <span class="stat-label">Total Facturado:</span>
          <strong class="text-emerald">S/ ${c.totalSpent.toFixed(2)}</strong>
        </div>
      </div>
    </div>
  `).join('');

  initIcons();
}

// ------------------------------------------------------------------------------
// WHATSAPP NOTIFIER MODAL
// ------------------------------------------------------------------------------
function openWhatsAppModal(job, tokenCode) {
  const phone = (job.customer_phone_snapshot || '').replace(/[^0-9]/g, '');
  const statusTexts = {
    received: 'recibido y está en cola de atención',
    in_progress: 'en proceso de impresión / copiado',
    ready: '¡LISTO PARA RECOGER!',
    delivered: 'entregado'
  };

  const statusStr = statusTexts[job.status] || job.status;
  const targetUrl = `https://qrscan.grafiplotvasquez.lat/t/${tokenCode}`;

  const message = `Hola *${job.customer_name_snapshot}*, te saludamos de Copiadora Grafiplot.

` +
    `Tu trabajo (*${job.description}*) se encuentra *${statusStr}*.
` +
    `Monto total: *S/ ${parseFloat(job.total).toFixed(2)}*.

` +
    `Puedes hacer seguimiento a tu boleta en tiempo real aquí:
${targetUrl}

` +
    `¡Gracias por tu preferencia!`;

  document.getElementById('wa-phone').value = phone;
  document.getElementById('wa-message').value = message;

  document.getElementById('send-wa-btn').onclick = () => {
    const targetPhone = document.getElementById('wa-phone').value.replace(/[^0-9]/g, '');
    const encodedMsg = encodeURIComponent(document.getElementById('wa-message').value);
    const waUrl = `https://wa.me/51${targetPhone}?text=${encodedMsg}`;
    window.open(waUrl, '_blank');
    closeModal('whatsapp-modal');
  };

  openModal('whatsapp-modal');
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
