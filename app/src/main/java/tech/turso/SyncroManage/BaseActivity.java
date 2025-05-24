package tech.turso.SyncroManage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

public abstract class BaseActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public static final String PREFS_APP_THEME = "AppThemePrefs";
    public static final String KEY_SELECTED_THEME = "SelectedTheme";
    public static final String THEME_VALUE_LIGHT = "light";
    public static final String THEME_VALUE_DARK = "dark";
    public static final String THEME_VALUE_ISABELLA = "isabella";
    public static final String THEME_VALUE_SYSTEM = "system";

    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    protected FirebaseAuth autenticacao;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyAppTheme();
        super.onCreate(savedInstanceState);
        autenticacao = FirebaseAuth.getInstance();
    }

    private void updateThemeMenuItemSelection() {
        if (navigationView == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_APP_THEME, MODE_PRIVATE);
        String currentTheme = prefs.getString(KEY_SELECTED_THEME, THEME_VALUE_SYSTEM);
        Menu menu = navigationView.getMenu();

        // Limpa seleções anteriores
        MenuItem lightTheme = menu.findItem(R.id.nav_theme_light);
        MenuItem darkTheme = menu.findItem(R.id.nav_theme_dark);
        MenuItem isabellaTheme = menu.findItem(R.id.nav_theme_isabella);
        MenuItem systemTheme = menu.findItem(R.id.nav_theme_system);

        // Desmarca todos os temas primeiro
        if (lightTheme != null) lightTheme.setChecked(false);
        if (darkTheme != null) darkTheme.setChecked(false);
        if (isabellaTheme != null) isabellaTheme.setChecked(false);
        if (systemTheme != null) systemTheme.setChecked(false);

        // Marca o tema atual
        switch (currentTheme) {
            case THEME_VALUE_LIGHT:
                if (lightTheme != null) lightTheme.setChecked(true);
                break;
            case THEME_VALUE_DARK:
                if (darkTheme != null) darkTheme.setChecked(true);
                break;
            case THEME_VALUE_ISABELLA:
                if (isabellaTheme != null) isabellaTheme.setChecked(true);
                break;
            case THEME_VALUE_SYSTEM:
            default:
                if (systemTheme != null) systemTheme.setChecked(true);
                break;
        }
    }

    protected void setupNavigationDrawer(int drawerLayoutId, int toolbarId, int navViewId, int checkedNavItemId) {
        Toolbar toolbar = findViewById(toolbarId);
        drawerLayout = findViewById(drawerLayoutId);
        navigationView = findViewById(navViewId);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        if (checkedNavItemId != 0) {
            navigationView.setCheckedItem(checkedNavItemId);
        }

        // Aplica as cores do tema ao menu lateral
        applyNavigationViewColors();

        updateThemeMenuItemSelection();
    }

    private void applyAppTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP_THEME, MODE_PRIVATE);
        String currentTheme = prefs.getString(KEY_SELECTED_THEME, THEME_VALUE_SYSTEM);

        if (THEME_VALUE_ISABELLA.equals(currentTheme)) {
            setTheme(R.style.Theme_Isabella);
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_VALUE_LIGHT.equals(currentTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_VALUE_DARK.equals(currentTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void applyNavigationViewColors() {
        if (navigationView == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_APP_THEME, MODE_PRIVATE);
        String currentTheme = prefs.getString(KEY_SELECTED_THEME, THEME_VALUE_SYSTEM);

        int backgroundColor;
        int textColor;
        int iconColor;

        if (THEME_VALUE_ISABELLA.equals(currentTheme)) {
            backgroundColor = ContextCompat.getColor(this, R.color.brand_secondary_light);
            textColor = ContextCompat.getColor(this, R.color.black);
            iconColor = ContextCompat.getColor(this, R.color.black);
        } else if (THEME_VALUE_DARK.equals(currentTheme)) {
            backgroundColor = ContextCompat.getColor(this, R.color.black);
            textColor = ContextCompat.getColor(this, R.color.white);
            iconColor = ContextCompat.getColor(this, R.color.white);
        } else {
            backgroundColor = ContextCompat.getColor(this, R.color.colorBackground);
            textColor = ContextCompat.getColor(this, R.color.colorOnSurface);
            iconColor = ContextCompat.getColor(this, R.color.colorOnSurface);
        }

        navigationView.setBackgroundColor(backgroundColor);
        navigationView.setItemTextColor(ColorStateList.valueOf(textColor));
        navigationView.setItemIconTintList(ColorStateList.valueOf(iconColor));
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        boolean themeChanged = false;
        String newThemeValue = null;

        // Lida com as entradas principais do menu
        if (id == R.id.nav_home) {
            startActivity(new Intent(this, Home.class));
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_vendas) {
            startActivity(new Intent(this, Vendas.class));
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_estoque) {
            startActivity(new Intent(this, EstoqueActivity.class));
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_servico) {
            startActivity(new Intent(this, Servicos.class));
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_relatorio) {
            startActivity(new Intent(this, Relatorios.class));
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        // Verifica itens de tema
        else if (id == R.id.nav_theme_light) {
            newThemeValue = THEME_VALUE_LIGHT;
            themeChanged = true;
        } else if (id == R.id.nav_theme_dark) {
            newThemeValue = THEME_VALUE_DARK;
            themeChanged = true;
        } else if (id == R.id.nav_theme_isabella) {
            newThemeValue = THEME_VALUE_ISABELLA;
            themeChanged = true;
        } else if (id == R.id.nav_theme_system) {
            newThemeValue = THEME_VALUE_SYSTEM;
            themeChanged = true;
        }

        // Verifica o item de sair
        else if (id == R.id.nav_sair) {
            if (autenticacao != null) {
                autenticacao.signOut();
            }
            Intent intent = new Intent(this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finishAffinity();
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        // Se um tema foi alterado, aplica o novo tema
        if (themeChanged && newThemeValue != null) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_APP_THEME, MODE_PRIVATE).edit();
            editor.putString(KEY_SELECTED_THEME, newThemeValue);
            editor.apply();

            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START, false);
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                applyNavigationViewColors(); // Atualiza o menu após mudar o tema
                recreate();
            }, 150);

            return true;
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}