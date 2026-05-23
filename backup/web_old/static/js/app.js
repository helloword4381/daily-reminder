/* 日常提醒工作记录 — 前端交互逻辑 */

(function() {
    'use strict';

    const API = '/api';

    // ── DOM refs ──
    const $ = id => document.getElementById(id);
    const todayLabel = $('todayLabel');
    const statDone = $('statDone');
    const statPending = $('statPending');
    const statTotal = $('statTotal');
    const taskInput = $('taskInput');
    const btnAdd = $('btnAdd');
    const completedList = $('completedList');
    const pendingList = $('pendingList');
    const btnSettings = $('btnSettings');
    const settingsModal = $('settingsModal');
    const closeSettings = $('closeSettings');
    const btnHistory = $('btnHistory');
    const historyModal = $('historyModal');
    const closeHistory = $('closeHistory');
    const historyDate = $('historyDate');
    const btnGoDate = $('btnGoDate');
    const dateChips = $('dateChips');
    const historyTasks = $('historyTasks');
    const morningTime = $('morningTime');
    const noonTime = $('noonTime');
    const morningEnabled = $('morningEnabled');
    const noonEnabled = $('noonEnabled');
    const btnSaveSettings = $('btnSaveSettings');
    const saveFeedback = $('saveFeedback');
    const editModal = $('editModal');
    const closeEdit = $('closeEdit');
    const editInput = $('editInput');
    const btnSaveEdit = $('btnSaveEdit');
    const btnDeleteTask = $('btnDeleteTask');

    // ── State ──
    let currentTasks = [];
    let editTaskId = null;

    // ── 日期格式化 ──
    function todayISO() {
        return new Date().toISOString().slice(0, 10);
    }

    function formatDateCN(isoStr) {
        const d = new Date(isoStr + 'T00:00:00');
        const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
        const m = d.getMonth() + 1;
        const day = d.getDate();
        const w = weekdays[d.getDay()];
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        if (isoStr === todayISO()) return '今天';
        if (isoStr === yesterday.toISOString().slice(0, 10)) return '昨天';
        return `${m}月${day}日 周${w}`;
    }

    function formatTime(dtStr) {
        if (!dtStr) return '';
        return dtStr.slice(11, 16);
    }

    // ── API 调用 ──
    async function api(url, options = {}) {
        try {
            const resp = await fetch(API + url, {
                headers: { 'Content-Type': 'application/json', ...options.headers },
                ...options,
            });
            if (!resp.ok) {
                const err = await resp.json().catch(() => ({ detail: resp.statusText }));
                throw new Error(err.detail || '请求失败');
            }
            return await resp.json();
        } catch (e) {
            console.error('API Error:', e);
            throw e;
        }
    }

    // ── 渲染任务列表 ──
    function renderTasks(tasks) {
        currentTasks = tasks || [];
        const completed = tasks.filter(t => t.status === 'completed');
        const pending = tasks.filter(t => t.status === 'unfinished');

        // 更新统计
        statDone.textContent = completed.length;
        statPending.textContent = pending.length;
        statTotal.textContent = tasks.length;

        // 渲染已完成
        if (completed.length === 0) {
            completedList.innerHTML = '<li class="empty-hint">暂无已完成的工作</li>';
        } else {
            completedList.innerHTML = completed.map(t => renderTaskItem(t)).join('');
        }

        // 渲染未完成
        if (pending.length === 0) {
            pendingList.innerHTML = '<li class="empty-hint">暂无未完成的工作</li>';
        } else {
            pendingList.innerHTML = pending.map(t => renderTaskItem(t)).join('');
        }
    }

    function renderTaskItem(task) {
        const isDone = task.status === 'completed';
        return `
            <li class="task-item ${isDone ? 'done' : ''}" data-id="${task.id}">
                <div class="task-check ${isDone ? 'done' : 'pending'}" data-action="toggle">
                    ${isDone ? '✓' : ''}
                </div>
                <span class="task-content">${escapeHtml(task.content)}</span>
                <span class="task-time">${formatTime(task.created_at)}</span>
            </li>
        `;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ── 加载今日任务 ──
    async function loadToday() {
        try {
            const data = await api('/tasks/today');
            todayLabel.textContent = formatDateCN(todayISO());
            renderTasks(data.tasks);
        } catch (e) {
            todayLabel.textContent = '加载失败';
            completedList.innerHTML = `<li class="empty-hint">❌ ${e.message}</li>`;
        }
    }

    // ── 添加任务 ──
    async function addTask() {
        const content = taskInput.value.trim();
        if (!content) {
            taskInput.focus();
            return;
        }
        try {
            await api('/tasks/', {
                method: 'POST',
                body: JSON.stringify({ content, task_date: todayISO() }),
            });
            taskInput.value = '';
            taskInput.focus();
            await loadToday();
        } catch (e) {
            alert('添加失败: ' + e.message);
        }
    }

    // ── 切换状态 ──
    async function toggleTask(taskId, currentStatus) {
        const newStatus = currentStatus === 'completed' ? 'unfinished' : 'completed';
        try {
            await api(`/tasks/${taskId}/status`, {
                method: 'PUT',
                body: JSON.stringify({ status: newStatus }),
            });
            await loadToday();
        } catch (e) {
            alert('更新失败: ' + e.message);
        }
    }

    // ── 打开编辑弹窗 ──
    function openEdit(taskId) {
        const task = currentTasks.find(t => t.id === taskId);
        if (!task) return;
        editTaskId = taskId;
        editInput.value = task.content;
        editModal.classList.remove('hidden');
        editInput.focus();
    }

    // ── 保存编辑 ──
    async function saveEdit() {
        const content = editInput.value.trim();
        if (!content) return;
        try {
            await api(`/tasks/${editTaskId}/content`, {
                method: 'PUT',
                body: JSON.stringify({ content }),
            });
            closeEditModal();
            await loadToday();
        } catch (e) {
            alert('编辑失败: ' + e.message);
        }
    }

    // ── 删除任务 ──
    async function deleteEditTask() {
        if (!confirm('确定删除这条记录？')) return;
        try {
            await api(`/tasks/${editTaskId}`, { method: 'DELETE' });
            closeEditModal();
            await loadToday();
        } catch (e) {
            alert('删除失败: ' + e.message);
        }
    }

    function closeEditModal() {
        editModal.classList.add('hidden');
        editTaskId = null;
    }

    // ── 事件委托：任务列表交互 ──
    document.querySelector('.task-sections').addEventListener('click', async (e) => {
        const item = e.target.closest('.task-item');
        if (!item) return;
        const taskId = parseInt(item.dataset.id);
        const action = e.target.dataset.action;

        if (action === 'toggle') {
            const task = currentTasks.find(t => t.id === taskId);
            if (task) await toggleTask(taskId, task.status);
        } else {
            // 点击任务本身 → 编辑
            openEdit(taskId);
        }
    });

    document.querySelector('.task-sections').addEventListener('touchstart', () => {
        // 防止移动端点击穿透
    }, { passive: true });

    // ── 加载设置 ──
    async function loadSettings() {
        try {
            const s = await api('/reminders/settings');
            morningTime.value = s.morning_time;
            noonTime.value = s.noon_time;
            morningEnabled.checked = s.morning_enabled;
            noonEnabled.checked = s.noon_enabled;
        } catch (e) {
            console.error('加载设置失败', e);
        }
    }

    // ── 保存设置 ──
    async function saveSettings() {
        const body = {
            morning_time: morningTime.value,
            noon_time: noonTime.value,
            morning_enabled: morningEnabled.checked,
            noon_enabled: noonEnabled.checked,
        };
        try {
            await api('/reminders/settings', {
                method: 'PUT',
                body: JSON.stringify(body),
            });
            saveFeedback.classList.remove('hidden');
            setTimeout(() => saveFeedback.classList.add('hidden'), 2000);
        } catch (e) {
            alert('保存设置失败: ' + e.message);
        }
    }

    // ── 历史记录 ──
    async function loadRecentDates() {
        try {
            const data = await api('/tasks/dates/recent?limit=14');
            const chips = data.dates;
            if (chips.length === 0) {
                dateChips.innerHTML = '<span class="hint">暂无历史记录</span>';
                return;
            }
            dateChips.innerHTML = chips.map(d =>
                `<span class="date-chip" data-date="${d}">${formatDateCN(d)}</span>`
            ).join('');
        } catch (e) {
            dateChips.innerHTML = '<span class="hint">加载失败</span>';
        }
    }

    async function loadHistoryTasks(dateStr) {
        try {
            const data = await api(`/tasks/${dateStr}`);
            if (data.tasks.length === 0) {
                historyTasks.innerHTML = '<p class="hint" style="text-align:center;padding:16px 0;">当天没有记录</p>';
                return;
            }
            historyTasks.innerHTML = `
                <p class="hint" style="margin-bottom:8px;">${formatDateCN(dateStr)} — ${data.count} 项</p>
                <div class="task-list">
                    ${data.tasks.map(t => renderTaskItem(t)).join('')}
                </div>
            `;
        } catch (e) {
            historyTasks.innerHTML = '<p class="hint">加载失败</p>';
        }
    }

    // ── 设置弹窗 ──
    btnSettings.addEventListener('click', () => {
        loadSettings();
        settingsModal.classList.remove('hidden');
    });
    closeSettings.addEventListener('click', () => settingsModal.classList.add('hidden'));
    settingsModal.addEventListener('click', (e) => {
        if (e.target === settingsModal) settingsModal.classList.add('hidden');
    });
    btnSaveSettings.addEventListener('click', saveSettings);

    // ── 历史弹窗 ──
    btnHistory.addEventListener('click', () => {
        historyDate.value = todayISO();
        historyTasks.innerHTML = '';
        loadRecentDates();
        historyModal.classList.remove('hidden');
    });
    closeHistory.addEventListener('click', () => historyModal.classList.add('hidden'));
    historyModal.addEventListener('click', (e) => {
        if (e.target === historyModal) historyModal.classList.add('hidden');
    });

    btnGoDate.addEventListener('click', () => {
        if (historyDate.value) loadHistoryTasks(historyDate.value);
    });
    historyDate.addEventListener('change', () => {
        if (historyDate.value) loadHistoryTasks(historyDate.value);
    });

    // 日期芯片点击
    dateChips.addEventListener('click', (e) => {
        const chip = e.target.closest('.date-chip');
        if (!chip) return;
        const d = chip.dataset.date;
        historyDate.value = d;
        // 高亮
        document.querySelectorAll('.date-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        loadHistoryTasks(d);
    });

    // ── 编辑弹窗 ──
    closeEdit.addEventListener('click', closeEditModal);
    editModal.addEventListener('click', (e) => {
        if (e.target === editModal) closeEditModal();
    });
    btnSaveEdit.addEventListener('click', saveEdit);
    btnDeleteTask.addEventListener('click', deleteEditTask);

    // ── 键盘事件 ──
    taskInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addTask();
        }
    });

    editInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            saveEdit();
        }
    });

    // ── 按钮事件 ──
    btnAdd.addEventListener('click', addTask);

    // ── 浏览器通知权限申请 ──
    function requestNotificationPermission() {
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }
    }

    // ── 初始化 ──
    async function init() {
        requestNotificationPermission();
        await loadToday();
    }

    // 页面可见时刷新（从其他页面切回时）
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) {
            loadToday();
        }
    });

    // ── PWA: 注册 Service Worker ──
    if ('serviceWorker' in navigator) {
        // 可选：后续添加 SW 支持离线
    }

    init();
})();
