# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['windows_app\\main.py'],
    pathex=[],
    binaries=[],
    datas=[('common', 'common')],
    hiddenimports=['windows_app', 'windows_app.db', 'windows_app.gui', 'windows_app.sync_engine', 'common', 'common.models', 'common.sync_protocol', 'plyer', 'plyer.platforms.win.notification', 'customtkinter'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='DailyReminder',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
