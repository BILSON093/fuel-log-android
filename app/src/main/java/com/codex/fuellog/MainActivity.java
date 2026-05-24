package com.codex.fuellog;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private final DecimalFormat one = new DecimalFormat("0.0");
    private final DecimalFormat two = new DecimalFormat("0.00");
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private Db db;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout bottomNav;
    private LinearLayout carCard;
    private TextView carNameView;
    private TextView carMetaView;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private String pendingExportType;
    private String pendingExportContent;
    private final List<Car> cars = new ArrayList<>();
    private long currentCarId = -1;
    private int selectedTab = 0;
    private static final int REQ_EXPORT_FILE = 41;
    private static final int REQ_IMPORT_FILE = 42;
    private final String[] tabTitles = {"首页", "记录", "图表", "费用", "设置"};
    private int accent = Color.rgb(31, 117, 107);
    private int accentDark = Color.rgb(18, 83, 78);
    private int ink = Color.rgb(26, 33, 35);
    private int muted = Color.rgb(104, 113, 112);
    private int paper = Color.rgb(245, 247, 243);
    private int surface = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        db = new Db(this);
        db.ensureSeed();
        requestNotificationPermission();
        buildShell();
        refreshCars();
        showDashboard();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(paper);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(34), dp(18), dp(12));
        top.setBackgroundColor(surface);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        screenTitle = label("车耗记", 24, true);
        screenSubtitle = label("记录每一次加油，长期看清真实成本", 13, false);
        screenSubtitle.setTextColor(muted);
        titles.addView(screenTitle);
        titles.addView(screenSubtitle);
        titleBar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button addFuel = primaryButton("+ 记录");
        addFuel.setOnClickListener(v -> showEnergyDialog(null));
        titleBar.addView(addFuel, new LinearLayout.LayoutParams(dp(104), dp(44)));
        top.addView(titleBar);

        carCard = new LinearLayout(this);
        carCard.setOrientation(LinearLayout.HORIZONTAL);
        carCard.setGravity(Gravity.CENTER_VERTICAL);
        carCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        carCard.setBackground(round(Color.rgb(243, 249, 246), dp(14), Color.rgb(211, 226, 220)));
        LinearLayout.LayoutParams carLp = new LinearLayout.LayoutParams(-1, -2);
        carLp.setMargins(0, dp(12), 0, 0);
        top.addView(carCard, carLp);
        TextView icon = label("CAR", 12, true);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(accentDark, dp(12), 0));
        carCard.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout carCopy = new LinearLayout(this);
        carCopy.setOrientation(LinearLayout.VERTICAL);
        carCopy.setPadding(dp(12), 0, dp(8), 0);
        carNameView = label("当前车辆", 17, true);
        carMetaView = label("点击切换或管理车辆", 13, false);
        carMetaView.setTextColor(muted);
        carMetaView.setSingleLine(false);
        carCopy.addView(carNameView);
        carCopy.addView(carMetaView);
        carCard.addView(carCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label("管理", 13, true);
        arrow.setTextColor(accentDark);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(round(Color.WHITE, dp(12), Color.rgb(211, 226, 220)));
        carCard.addView(arrow, new LinearLayout.LayoutParams(dp(62), dp(40)));
        carCard.setOnClickListener(v -> showCarManager());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(8), dp(8), dp(8), dp(10));
        bottomNav.setBackground(round(surface, dp(18), 0));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, dp(78));
        navLp.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(bottomNav, navLp);
        buildBottomNav();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(pendingExportContent.getBytes("UTF-8"));
                toast("已保存导出文件");
            } catch (Exception e) {
                toast("保存失败：" + e.getMessage());
            }
        }
        if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importBackup(data.getData());
        }
        pendingExportType = null;
        pendingExportContent = null;
    }

    private void renderCurrentTab() {
        if (content == null || currentCarId < 0) return;
        if (selectedTab == 0) showDashboard();
        if (selectedTab == 1) showRecords();
        if (selectedTab == 2) showCharts();
        if (selectedTab == 3) showCosts();
        if (selectedTab == 4) showSettings();
    }

    private void buildBottomNav() {
        if (bottomNav == null) return;
        bottomNav.removeAllViews();
        for (int i = 0; i < tabTitles.length; i++) {
            final int index = i;
            Button b = navButton(tabTitles[i], selectedTab == index);
            b.setOnClickListener(v -> {
                selectedTab = index;
                renderCurrentTab();
            });
            bottomNav.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
    }

    private void setHeader(String title, String subtitle) {
        if (screenTitle != null) screenTitle.setText(title);
        if (screenSubtitle != null) screenSubtitle.setText(subtitle);
        buildBottomNav();
    }

    private void refreshCars() {
        cars.clear();
        cars.addAll(db.cars());
        if (cars.isEmpty()) {
            db.insertCar("我的车", "", "", "汽油", "92", 0, 50, true);
            cars.addAll(db.cars());
        }
        long saved = getPreferences(MODE_PRIVATE).getLong("default_car", cars.get(0).id);
        int selected = 0;
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            if (c.id == saved) selected = i;
        }
        currentCarId = cars.get(selected).id;
        updateCarHeader();
    }

    private void saveDefaultCar(long id) {
        getPreferences(MODE_PRIVATE).edit().putLong("default_car", id).apply();
    }

    private void updateCarHeader() {
        Car c = currentCar();
        if (carNameView != null) carNameView.setText(c.name);
        if (carMetaView != null) {
            String capacity = isElectric(c) ? "电池 " + one.format(c.tankLiters) + "kWh" : "油箱 " + one.format(c.tankLiters) + "L";
            String meta = (c.plate.isEmpty() ? "未填写车牌" : c.plate) + " · " + c.fuelType + " · " + c.defaultFuel + "\n" + capacity;
            carMetaView.setText(meta);
        }
    }

    private boolean isElectric(Car c) {
        return c != null && ((c.fuelType != null && c.fuelType.contains("电")) || (c.defaultFuel != null && c.defaultFuel.contains("充")));
    }

    private String energyAction() {
        return isElectric(currentCar()) ? "+ 充电" : "+ 加油";
    }

    private String consumptionName() {
        return isElectric(currentCar()) ? "电耗" : "油耗";
    }

    private String consumptionUnit() {
        return isElectric(currentCar()) ? "kWh/100km" : "L/100km";
    }

    private String amountUnit() {
        return isElectric(currentCar()) ? "kWh" : "L";
    }

    private String priceUnit() {
        return isElectric(currentCar()) ? "元/kWh" : "元/L";
    }

    private String stationName() {
        return isElectric(currentCar()) ? "充电站" : "加油站";
    }

    private String energyTable() {
        return isElectric(currentCar()) ? "charging_records" : "fuel_records";
    }

    private void showDashboard() {
        selectedTab = 0;
        setHeader("车耗记", "一眼看清油耗电耗和用车成本");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        Stats s = db.stats(energyTable(), currentCarId);
        box.addView(hero(s));
        LinearLayout quick = row();
        Button fuel = primaryButton(energyAction());
        fuel.setOnClickListener(v -> showEnergyDialog(null));
        Button upkeep = quietButton("记保养");
        upkeep.setOnClickListener(v -> showMaintenanceDialog(null));
        Button expense = quietButton("记费用");
        expense.setOnClickListener(v -> showExpenseDialog(null));
        quick.addView(fuel, new LinearLayout.LayoutParams(0, dp(50), 1.3f));
        quick.addView(upkeep, new LinearLayout.LayoutParams(0, dp(50), 1));
        quick.addView(expense, new LinearLayout.LayoutParams(0, dp(50), 1));
        box.addView(quick);
        box.addView(sectionTitle("本车概览"));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        box.addView(grid);
        LinearLayout row1 = row();
        row1.addView(metric("平均" + consumptionName(), s.avgConsumption > 0 ? two.format(s.avgConsumption) + " " + consumptionUnit() : "暂无", 1));
        row1.addView(metric("最近" + consumptionName(), s.lastConsumption > 0 ? two.format(s.lastConsumption) + " " + consumptionUnit() : "暂无", 1));
        grid.addView(row1);
        LinearLayout row2 = row();
        row2.addView(metric(isElectric(currentCar()) ? "本月电费" : "本月油费", "¥" + two.format(s.monthFuelCost), 1));
        row2.addView(metric("真实每公里", s.realCostPerKm > 0 ? "¥" + two.format(s.realCostPerKm) : "暂无", 1));
        grid.addView(row2);
        LinearLayout row3 = row();
        row3.addView(metric("总里程", one.format(s.totalDistance) + " km", 1));
        row3.addView(metric("总支出", "¥" + two.format(s.totalCost), 1));
        grid.addView(row3);

        if (s.avgConsumption > 0 && s.lastConsumption > s.avgConsumption * 1.25) {
            box.addView(warn("最近一次" + consumptionName() + "高于平均值 25% 以上，可检查胎压、路况或数据输入。"));
        }
        if (s.recentDaysWithoutFuel > 30) {
            box.addView(warn("超过 30 天没有" + (isElectric(currentCar()) ? "充电" : "加油") + "记录，若最近记录过可以补记。"));
        }

        box.addView(sectionTitle(isElectric(currentCar()) ? "最近充电" : "最近加油"));
        List<Fuel> fuels = db.fuels(energyTable(), currentCarId, 3);
        if (fuels.isEmpty()) {
            box.addView(empty(isElectric(currentCar()) ? "还没有充电记录。第一次充满作为基准，之后就能计算电耗。" : "还没有加油记录。第一次加满作为基准，之后就能计算油耗。"));
        } else {
            for (Fuel f : fuels) box.addView(fuelRow(f, false, energyTable()));
        }
        rootAdd(scroll);
    }

    private void addRecordActions(LinearLayout box) {
        LinearLayout panel = card();
        panel.addView(label("快速记录", 18, true));
        TextView hint = label((isElectric(currentCar()) ? "充电" : "加油") + "是主线，保养和其他费用用于计算真实用车成本。", 13, false);
        hint.setTextColor(muted);
        hint.setPadding(0, dp(2), 0, dp(10));
        panel.addView(hint);
        LinearLayout actions = row();
        Button fuel = primaryButton(energyAction());
        fuel.setOnClickListener(v -> showEnergyDialog(null));
        Button maintenance = quietButton("保养");
        maintenance.setOnClickListener(v -> showMaintenanceDialog(null));
        Button expense = quietButton("费用");
        expense.setOnClickListener(v -> showExpenseDialog(null));
        actions.addView(fuel, new LinearLayout.LayoutParams(0, dp(48), 1.25f));
        actions.addView(maintenance, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(expense, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(actions);
        box.addView(panel);
    }

    private void showAdd() {
        showEnergyDialog(null);
    }

    private void showRecords() {
        selectedTab = 1;
        setHeader("记录", "查看、编辑或长按删除历史记录");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        addRecordActions(box);
        box.addView(sectionTitle(isElectric(currentCar()) ? "充电记录" : "加油记录"));
        List<Fuel> fuels = db.fuels(energyTable(), currentCarId, 0);
        if (fuels.isEmpty()) box.addView(empty(isElectric(currentCar()) ? "暂无充电记录" : "暂无加油记录"));
        for (Fuel f : fuels) box.addView(fuelRow(f, true, energyTable()));
        box.addView(sectionTitle("保养记录"));
        List<Entry> ms = db.entries("maintenance_records", currentCarId, 0);
        if (ms.isEmpty()) box.addView(empty("暂无保养记录"));
        for (Entry e : ms) box.addView(entryRow("maintenance_records", e));
        box.addView(sectionTitle("其他费用"));
        List<Entry> es = db.entries("expense_records", currentCarId, 0);
        if (es.isEmpty()) box.addView(empty("暂无费用记录"));
        for (Entry e : es) box.addView(entryRow("expense_records", e));
        rootAdd(scroll);
    }

    private void showCharts() {
        selectedTab = 2;
        setHeader("图表", "趋势、月度费用和加油站占比");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        box.addView(chartHero());
        box.addView(sectionTitle("数据可视化"));
        List<FuelPoint> points = db.fuelPoints(energyTable(), currentCarId);
        box.addView(chartCard(consumptionName() + "趋势 " + consumptionUnit(), new ChartView(this, points, ChartView.MODE_CONSUMPTION)));
        box.addView(chartCard((isElectric(currentCar()) ? "电价趋势 " : "油价趋势 ") + priceUnit(), new ChartView(this, points, ChartView.MODE_PRICE)));
        box.addView(chartCard(isElectric(currentCar()) ? "月度电费" : "月度油费", new ChartView(this, db.monthPoints(energyTable(), currentCarId, "fuel"), ChartView.MODE_BAR)));
        box.addView(chartCard("月度总用车成本", new ChartView(this, db.monthPoints(energyTable(), currentCarId, "all"), ChartView.MODE_BAR)));
        box.addView(chartCard(stationName() + "费用占比", new ChartView(this, db.stationPoints(energyTable(), currentCarId), ChartView.MODE_PIE)));
        rootAdd(scroll);
    }

    private void showCosts() {
        selectedTab = 3;
        setHeader("费用", "把加油、保养和其他支出合在一起看");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        Stats s = db.stats(energyTable(), currentCarId);
        box.addView(costHero(s));
        box.addView(sectionTitle("费用分析"));
        LinearLayout row1 = row();
        row1.addView(metric(isElectric(currentCar()) ? "充电" : "加油", "¥" + two.format(s.fuelCost), 1));
        row1.addView(metric("保养", "¥" + two.format(s.maintenanceCost), 1));
        row1.addView(metric("其他", "¥" + two.format(s.expenseCost), 1));
        box.addView(row1);
        box.addView(metric("总用车成本", "¥" + two.format(s.totalCost), 0));
        box.addView(metric("平均每公里真实成本", s.realCostPerKm > 0 ? "¥" + two.format(s.realCostPerKm) + "/km" : "暂无", 0));
        box.addView(sectionTitle("费用分类"));
        for (Entry e : db.expenseSummary(currentCarId)) {
            box.addView(metric(e.title, "¥" + two.format(e.amount), 0));
        }
        rootAdd(scroll);
    }

    private void showSettings() {
        selectedTab = 4;
        setHeader("设置", "车辆、备份、提醒和数据管理");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        box.addView(settingsHero());
        box.addView(sectionTitle("常用设置"));
        Button carsButton = primaryButton("管理车辆");
        carsButton.setOnClickListener(v -> showCarManager());
        Button reminder = quietButton("创建本地提醒");
        reminder.setOnClickListener(v -> showReminderDialog());
        box.addView(settingRow("车辆资料", "维护车辆名称、油品、初始里程和油箱容量", carsButton));
        box.addView(settingRow("本地提醒", "保养、保险或年检日期提醒", reminder));

        box.addView(sectionTitle("数据备份"));
        Button exportJson = quietButton("选择位置保存 JSON");
        exportJson.setOnClickListener(v -> exportJson());
        Button exportCsv = quietButton("选择位置保存 CSV");
        exportCsv.setOnClickListener(v -> exportCsv());
        Button importJson = quietButton("导入 JSON 备份");
        importJson.setOnClickListener(v -> chooseImportFile());
        box.addView(settingRow("完整备份", "包含车辆、加油、保养、费用和提醒", exportJson));
        box.addView(settingRow("表格导出", "当前车辆加油记录，可用 Excel 查看", exportCsv));
        box.addView(settingRow("恢复备份", "从 JSON 文件恢复全部车辆和记录", importJson));

        box.addView(sectionTitle("危险操作"));
        Button clear = dangerButton("清空当前车辆数据");
        clear.setOnClickListener(v -> confirmClear());
        box.addView(dangerPanel("只删除当前车辆的加油、保养、费用和提醒，车辆资料保留。", clear));
        rootAdd(scroll);
    }

    private View fuelRow(Fuel f, boolean editable, String table) {
        boolean ev = isElectric(currentCar());
        LinearLayout card = card();
        LinearLayout top = row();
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView date = label(f.date, 16, true);
        TextView odo = label(one.format(f.odometer) + " km", 13, false);
        odo.setTextColor(muted);
        left.addView(date);
        left.addView(odo);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        String status = f.full ? (ev ? "充满" : "加满") : (ev ? "未充满" : "未加满");
        if (f.missed) status += " · 漏记";
        top.addView(tag(status, f.full ? accentDark : Color.rgb(183, 117, 43)));
        card.addView(top);

        LinearLayout focus = row();
        focus.setPadding(0, dp(12), 0, dp(10));
        LinearLayout consumption = new LinearLayout(this);
        consumption.setOrientation(LinearLayout.VERTICAL);
        TextView cLabel = label("本次" + consumptionName(), 12, false);
        cLabel.setTextColor(muted);
        TextView cValue = label(f.consumption > 0 ? two.format(f.consumption) : "--", 30, true);
        cValue.setTextColor(f.consumption > 0 ? accentDark : muted);
        TextView cUnit = label(f.consumption > 0 ? consumptionUnit() : (ev ? "待下次充满计算" : "待下次加满计算"), 12, false);
        cUnit.setTextColor(muted);
        consumption.addView(cLabel);
        consumption.addView(cValue);
        consumption.addView(cUnit);
        focus.addView(consumption, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout money = new LinearLayout(this);
        money.setOrientation(LinearLayout.VERTICAL);
        money.setGravity(Gravity.END);
        TextView amount = label("¥" + two.format(f.amount), 24, true);
        amount.setGravity(Gravity.END);
        TextView liters = label(one.format(f.liters) + " " + amountUnit(), 14, false);
        liters.setTextColor(muted);
        liters.setGravity(Gravity.END);
        money.addView(amount);
        money.addView(liters);
        focus.addView(money, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(focus);

        LinearLayout chips = row();
        chips.addView(infoChip("区间", f.distance > 0 ? one.format(f.distance) + " km" : "--"));
        chips.addView(infoChip(ev ? "电价" : "油价", f.price > 0 ? two.format(f.price) + " " + priceUnit() : "--"));
        chips.addView(infoChip(ev ? "充电类型" : "油品", f.fuelType.isEmpty() ? "--" : f.fuelType));
        card.addView(chips);

        if (!f.station.isEmpty() || !f.note.isEmpty()) {
            TextView meta = label((f.station.isEmpty() ? "未填写" + stationName() : f.station) + (f.note.isEmpty() ? "" : " · " + f.note), 13, false);
            meta.setTextColor(muted);
            meta.setPadding(0, dp(10), 0, 0);
            card.addView(meta);
        }
        if (editable) {
            card.setOnClickListener(v -> showEnergyDialog(f));
            card.setOnLongClickListener(v -> {
                confirmDelete(table, f.id);
                return true;
            });
        }
        return card;
    }

    private View entryRow(String table, Entry e) {
        LinearLayout card = card();
        LinearLayout top = row();
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label(e.title, 17, true));
        TextView sub = label(e.date + " · " + one.format(e.odometer) + " km", 13, false);
        sub.setTextColor(muted);
        left.addView(sub);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = label("¥" + two.format(e.amount), 22, true);
        amount.setGravity(Gravity.END);
        top.addView(amount);
        card.addView(top);
        if (!e.place.isEmpty() || !e.note.isEmpty()) {
            TextView meta = label((e.place.isEmpty() ? "" : e.place) + (e.note.isEmpty() ? "" : " · " + e.note), 13, false);
            meta.setTextColor(muted);
            meta.setPadding(0, dp(8), 0, 0);
            card.addView(meta);
        }
        card.setOnClickListener(v -> {
            if (table.equals("maintenance_records")) showMaintenanceDialog(e);
            else showExpenseDialog(e);
        });
        card.setOnLongClickListener(v -> {
            confirmDelete(table, e.id);
            return true;
        });
        return card;
    }

    private void showEnergyDialog(Fuel existing) {
        if (isElectric(currentCar())) showChargeDialog(existing);
        else showFuelDialog(existing);
    }

    private void showFuelDialog(Fuel existing) {
        boolean ev = false;
        LinearLayout form = dialogForm();
        form.addView(formHero("记录这一次加油", "填加油量、金额或油价中的任意两项，第三项自动计算。"));
        EditText date = input("请选择日期", existing == null ? today() : existing.date, false);
        EditText odo = input("例如 35680", existing == null ? "" : one.format(existing.odometer), true);
        EditText liters = input(ev ? "例如 38.5" : "例如 42.5", existing == null ? "" : one.format(existing.liters), true);
        EditText amount = input(ev ? "例如 62" : "例如 320", existing == null ? "" : two.format(existing.amount), true);
        EditText price = input("例如 7.53", existing == null ? defaultEnergyPrice() : two.format(existing.price), true);
        EditText station = input(ev ? "特来电 / 星星充电 / 家充" : "中石化 / 壳牌 / 其他", existing == null ? "" : existing.station, false);
        EditText fuelType = input(ev ? "快充 / 慢充 / 家充" : "92 / 95 / 98 / 柴油", existing == null ? currentCar().defaultFuel : existing.fuelType, false);
        fuelType.setOnClickListener(v -> chooseValue(fuelType, ev ? new String[]{"快充", "慢充", "家充", "公共充电", "免费充电"} : new String[]{"92", "95", "98", "柴油"}));
        CheckBox full = check(ev ? "本次充满" : "本次加满", existing == null || existing.full);
        CheckBox missed = check("漏记后补录/跳过本区间" + consumptionName(), existing != null && existing.missed);
        EditText note = input(ev ? "充电速度、停车费、优惠等" : "路况、优惠、驾驶情况等", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));

        LinearLayout basic = formSection("基础信息");
        basic.addView(field("日期", date));
        basic.addView(field("当前总里程 km", odo));
        form.addView(basic);

        LinearLayout fuel = formSection(ev ? "电量与金额" : "油量与金额");
        fuel.addView(twoFields(field(ev ? "充电量 kWh" : "加油量 L", liters), field(ev ? "电费 元" : "金额 元", amount)));
        fuel.addView(twoFields(field(ev ? "电价 元/kWh" : "油价 元/L", price), field(ev ? "充电类型" : "油品", fuelType)));
        fuel.addView(checkRow(full, missed));
        form.addView(fuel);

        LinearLayout extra = formSection("补充信息");
        extra.addView(field(stationName(), station));
        extra.addView(tallField("备注", note));
        form.addView(extra);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? (ev ? "新增充电" : "新增加油") : (ev ? "编辑充电" : "编辑加油"))
                .setView(scrollForm(form))
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double l = num(liters), a = num(amount), p = num(price);
            if (l <= 0 && a > 0 && p > 0) l = a / p;
            if (a <= 0 && l > 0 && p > 0) a = l * p;
            if (p <= 0 && l > 0 && a > 0) p = a / l;
            if (num(odo) <= 0 || l <= 0) {
                toast("里程和加油量必须大于 0");
                return;
            }
            if (!validOdometer(existing == null ? -1 : existing.id, num(odo))) {
                toast("里程不能小于上一条记录，可编辑历史记录修正");
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put("car_id", currentCarId);
            cv.put("date", text(date, today()));
            cv.put("odometer", num(odo));
            cv.put("liters", l);
            cv.put("amount", a);
            cv.put("price", p);
            cv.put("station", text(station, ""));
            cv.put("fuel_type", text(fuelType, ""));
            cv.put("is_full", full.isChecked() ? 1 : 0);
            cv.put("is_missed", missed.isChecked() ? 1 : 0);
            cv.put("note", text(note, ""));
            if (existing == null) db.insert("fuel_records", cv);
            else db.update("fuel_records", existing.id, cv);
            dialog.dismiss();
            hideKeyboard(odo);
            renderCurrentTab();
        }));
        dialog.show();
    }

    private void showChargeDialog(Fuel existing) {
        LinearLayout form = dialogForm();
        form.addView(formHero("记录这一次充电", "按充电场景记录电量、电费和充电方式，电耗独立统计。"));
        EditText date = input("请选择日期", existing == null ? today() : existing.date, false);
        EditText odo = input("例如 35680", existing == null ? "" : one.format(existing.odometer), true);
        EditText kwh = input("例如 38.5", existing == null ? "" : one.format(existing.liters), true);
        EditText fee = input("例如 62", existing == null ? "" : two.format(existing.amount), true);
        EditText price = input("例如 1.60", existing == null ? defaultEnergyPrice() : two.format(existing.price), true);
        EditText station = input("特来电 / 星星充电 / 家充", existing == null ? "" : existing.station, false);
        EditText chargeType = input("快充 / 慢充 / 家充", existing == null ? currentCar().defaultFuel : existing.fuelType, false);
        chargeType.setOnClickListener(v -> chooseValue(chargeType, new String[]{"快充", "慢充", "家充", "公共充电", "免费充电"}));
        CheckBox full = check("本次充满", existing == null || existing.full);
        CheckBox missed = check("漏记后补录/跳过本区间电耗", existing != null && existing.missed);
        EditText parking = input("例如 停车费 8 元 / 免费停车", "", false);
        EditText note = input("充电速度、排队、优惠、电池温度等", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));

        LinearLayout basic = formSection("基础信息");
        basic.addView(field("日期", date));
        basic.addView(field("当前总里程 km", odo));
        form.addView(basic);

        LinearLayout charge = formSection("充电数据");
        charge.addView(twoFields(field("充电量 kWh", kwh), field("电费 元", fee)));
        charge.addView(twoFields(field("电价 元/kWh", price), field("充电方式", chargeType)));
        charge.addView(checkRow(full, missed));
        form.addView(charge);

        LinearLayout place = formSection("场景信息");
        place.addView(field("充电站/位置", station));
        place.addView(field("停车/服务费", parking));
        place.addView(tallField("备注", note));
        form.addView(place);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增充电" : "编辑充电")
                .setView(scrollForm(form))
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double l = num(kwh), a = num(fee), p = num(price);
            if (l <= 0 && a > 0 && p > 0) l = a / p;
            if (a <= 0 && l > 0 && p > 0) a = l * p;
            if (p <= 0 && l > 0 && a > 0) p = a / l;
            if (num(odo) <= 0 || l <= 0) {
                toast("里程和充电量必须大于 0");
                return;
            }
            if (!validOdometer(existing == null ? -1 : existing.id, num(odo))) {
                toast("里程不能小于上一条记录，可编辑历史记录修正");
                return;
            }
            String noteText = text(note, "");
            String parkingText = text(parking, "");
            if (!parkingText.isEmpty()) noteText = noteText.isEmpty() ? parkingText : parkingText + " · " + noteText;
            ContentValues cv = new ContentValues();
            cv.put("car_id", currentCarId);
            cv.put("date", text(date, today()));
            cv.put("odometer", num(odo));
            cv.put("liters", l);
            cv.put("amount", a);
            cv.put("price", p);
            cv.put("station", text(station, ""));
            cv.put("fuel_type", text(chargeType, ""));
            cv.put("is_full", full.isChecked() ? 1 : 0);
            cv.put("is_missed", missed.isChecked() ? 1 : 0);
            cv.put("note", noteText);
            if (existing == null) db.insert("charging_records", cv);
            else db.update("charging_records", existing.id, cv);
            dialog.dismiss();
            hideKeyboard(odo);
            renderCurrentTab();
        }));
        dialog.show();
    }

    private void showMaintenanceDialog(Entry existing) {
        LinearLayout form = dialogForm();
        form.addView(formHero("记录保养", "保养费用会计入真实每公里成本。"));
        EditText date = input("请选择日期", existing == null ? today() : existing.date, false);
        EditText odo = input("例如 35680", existing == null ? "" : one.format(existing.odometer), true);
        EditText title = input("机油 / 机滤 / 轮胎 / 保险", existing == null ? "机油/机滤" : existing.title, false);
        EditText amount = input("例如 480", existing == null ? "" : two.format(existing.amount), true);
        EditText place = input("4S 店 / 修理厂 / 门店", existing == null ? "" : existing.place, false);
        EditText note = input("配件品牌、下次保养里程等", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));
        LinearLayout basic = formSection("基础信息");
        basic.addView(field("日期", date));
        basic.addView(field("当前总里程 km", odo));
        form.addView(basic);
        LinearLayout detail = formSection("保养内容");
        detail.addView(field("保养类型", title));
        detail.addView(twoFields(field("金额 元", amount), field("门店", place)));
        detail.addView(tallField("备注", note));
        form.addView(detail);
        entryDialog(existing, "maintenance_records", "保养记录", form, date, odo, title, amount, place, note);
    }

    private void showExpenseDialog(Entry existing) {
        LinearLayout form = dialogForm();
        form.addView(formHero("记录费用", "停车、过路、洗车等都会进入总用车成本。"));
        EditText date = input("请选择日期", existing == null ? today() : existing.date, false);
        EditText odo = input("例如 35680", existing == null ? "" : one.format(existing.odometer), true);
        EditText title = input("停车费 / 过路费 / 洗车", existing == null ? "停车费" : existing.title, false);
        EditText amount = input("例如 35", existing == null ? "" : two.format(existing.amount), true);
        EditText place = input("地点或商户", existing == null ? "" : existing.place, false);
        EditText note = input("备注", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));
        LinearLayout basic = formSection("基础信息");
        basic.addView(field("日期", date));
        basic.addView(field("当前总里程 km", odo));
        form.addView(basic);
        LinearLayout detail = formSection("费用内容");
        detail.addView(field("费用类型", title));
        detail.addView(twoFields(field("金额 元", amount), field("地点/商户", place)));
        detail.addView(tallField("备注", note));
        form.addView(detail);
        entryDialog(existing, "expense_records", "费用记录", form, date, odo, title, amount, place, note);
    }

    private void entryDialog(Entry existing, String table, String titleText, LinearLayout form, EditText date, EditText odo, EditText title, EditText amount, EditText place, EditText note) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增" + titleText : "编辑" + titleText)
                .setView(scrollForm(form))
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (text(title, "").isEmpty() || num(amount) <= 0) {
                toast("类型和金额不能为空");
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put("car_id", currentCarId);
            cv.put("date", text(date, today()));
            cv.put("odometer", num(odo));
            cv.put("title", text(title, ""));
            cv.put("amount", num(amount));
            cv.put("place", text(place, ""));
            cv.put("note", text(note, ""));
            if (existing == null) db.insert(table, cv);
            else db.update(table, existing.id, cv);
            dialog.dismiss();
            renderCurrentTab();
        }));
        dialog.show();
    }

    private void showCarManager() {
        LinearLayout list = dialogForm();
        list.addView(formHero("车辆管理", "点击车辆切换当前车辆，编辑可修改资料。"));
        final AlertDialog[] holder = new AlertDialog[1];
        for (Car c : db.cars()) {
            list.addView(carManagerRow(c, () -> {
                if (holder[0] != null) holder[0].dismiss();
            }));
        }
        Button add = primaryButton("+ 添加车辆");
        add.setOnClickListener(v -> showCarDialog(null));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(50));
        addLp.setMargins(0, dp(4), 0, 0);
        list.addView(add, addLp);
        holder[0] = new AlertDialog.Builder(this).setTitle("车辆管理").setView(scrollForm(list)).setNegativeButton("关闭", null).create();
        holder[0].show();
    }

    private View carManagerRow(Car c, Runnable afterSelect) {
        LinearLayout card = card();
        LinearLayout top = row();
        TextView avatar = label(isElectric(c) ? "电" : (c.defaultFuel.isEmpty() ? "车" : c.defaultFuel), 13, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setBackground(round(c.id == currentCarId ? accentDark : Color.rgb(91, 93, 86), dp(12), 0));
        top.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView name = label(c.name + (c.id == currentCarId ? " · 当前" : ""), 17, true);
        TextView meta = label((c.plate.isEmpty() ? "未填写车牌" : c.plate) + " · " + c.fuelType + " · " + (isElectric(c) ? "电池 " + one.format(c.tankLiters) + "kWh" : "油箱 " + one.format(c.tankLiters) + "L"), 13, false);
        meta.setTextColor(muted);
        copy.addView(name);
        copy.addView(meta);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Button edit = quietButton("编辑");
        edit.setOnClickListener(v -> showCarDialog(c));
        top.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(42)));
        card.addView(top);
        card.setOnClickListener(v -> {
            currentCarId = c.id;
            saveDefaultCar(c.id);
            updateCarHeader();
            renderCurrentTab();
            toast("已切换到 " + c.name);
            if (afterSelect != null) afterSelect.run();
        });
        return card;
    }

    private void showCarDialog(Car c) {
        LinearLayout form = dialogForm();
        form.addView(formHero(c == null ? "添加车辆" : "编辑车辆", "车辆资料会影响默认油品、统计和当前车辆切换。"));
        EditText name = input("例如 我的车", c == null ? "我的车" : c.name, false);
        EditText brand = input("品牌 / 型号", c == null ? "" : c.brand, false);
        EditText plate = input("车牌号", c == null ? "" : c.plate, false);
        plate.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(8)});
        plate.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        EditText fuelType = input("汽油 / 柴油 / 混动 / 电车", c == null ? "汽油" : c.fuelType, false);
        EditText defaultFuel = input("92 / 95 / 98 / 快充", c == null ? "92" : c.defaultFuel, false);
        EditText initial = input("例如 0", c == null ? "0" : one.format(c.initialOdometer), true);
        EditText tank = input("例如 50", c == null ? "50" : one.format(c.tankLiters), true);
        defaultFuel.setOnClickListener(v -> {
            boolean ev = fuelType.getText().toString().contains("电");
            chooseValue(defaultFuel, ev ? new String[]{"快充", "慢充", "家充", "公共充电"} : new String[]{"92", "95", "98", "柴油"});
        });
        LinearLayout basic = formSection("车辆信息");
        basic.addView(field("车辆名称", name));
        basic.addView(twoFields(field("品牌/型号", brand), field("车牌号", plate)));
        form.addView(basic);
        LinearLayout fuel = formSection("能源设置");
        fuel.addView(energyTypeButtons(fuelType, defaultFuel, tank));
        fuel.addView(field("默认油品/充电", defaultFuel));
        fuel.addView(twoFields(field("初始里程 km", initial), field("油箱/电池容量", tank)));
        form.addView(fuel);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(c == null ? "添加车辆" : "编辑车辆")
                .setView(scrollForm(form))
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (text(name, "").isEmpty()) {
                toast("车辆名称不能为空");
                return;
            }
            if (c == null) {
                long id = db.insertCar(text(name, ""), text(brand, ""), text(plate, ""), text(fuelType, "汽油"), text(defaultFuel, "92"), num(initial), num(tank), false);
                saveDefaultCar(id);
            } else {
                ContentValues cv = new ContentValues();
                cv.put("name", text(name, ""));
                cv.put("brand", text(brand, ""));
                cv.put("plate", text(plate, ""));
                cv.put("fuel_type", text(fuelType, ""));
                cv.put("default_fuel", text(defaultFuel, ""));
                cv.put("initial_odometer", num(initial));
                cv.put("tank_liters", num(tank));
                db.update("cars", c.id, cv);
            }
            dialog.dismiss();
            refreshCars();
            renderCurrentTab();
        }));
        dialog.show();
    }

    private void showReminderDialog() {
        LinearLayout form = dialogForm();
        EditText title = input("标题", "保养提醒", false);
        EditText msg = input("内容", "车辆该保养了", false);
        EditText date = input("提醒日期 yyyy-MM-dd", today(), false);
        date.setOnClickListener(v -> pickDate(date));
        form.addView(title); form.addView(msg); form.addView(date);
        new AlertDialog.Builder(this)
                .setTitle("创建本地提醒")
                .setView(form)
                .setPositiveButton("保存", (d, which) -> scheduleReminder(text(title, ""), text(msg, ""), text(date, today())))
                .setNegativeButton("取消", null)
                .show();
    }

    private void scheduleReminder(String title, String msg, String dateText) {
        try {
            Calendar cal = Calendar.getInstance();
            String[] parts = dateText.split("-");
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]), 9, 0, 0);
            Intent intent = new Intent(this, ReminderReceiver.class);
            intent.putExtra("title", title);
            intent.putExtra("message", msg);
            PendingIntent pending = PendingIntent.getBroadcast(this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarm.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pending);
            ContentValues cv = new ContentValues();
            cv.put("car_id", currentCarId);
            cv.put("title", title);
            cv.put("message", msg);
            cv.put("due_date", dateText);
            db.insert("reminders", cv);
            toast("提醒已创建");
        } catch (Exception e) {
            toast("日期格式不正确");
        }
    }

    private void confirmDelete(String table, long id) {
        new AlertDialog.Builder(this).setTitle("删除记录？")
                .setMessage("删除后统计会自动重新计算。")
                .setPositiveButton("删除", (d, w) -> {
                    db.delete(table, id);
                    renderCurrentTab();
                })
                .setNegativeButton("取消", null).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("清空当前车辆数据？")
                .setMessage("只删除当前车辆的加油、保养、费用和提醒记录，车辆本身保留。")
                .setPositiveButton("清空", (d, w) -> {
                    db.clearCarData(currentCarId);
                    renderCurrentTab();
                })
                .setNegativeButton("取消", null).show();
    }

    private void exportJson() {
        try {
            chooseExportFile("fuel-log-backup.json", "application/json", db.exportJson().toString(2));
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private void exportCsv() {
        try {
            chooseExportFile(isElectric(currentCar()) ? "charging-records.csv" : "fuel-records.csv", "text/csv", db.exportEnergyCsv(energyTable(), currentCarId));
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private void chooseExportFile(String fileName, String mimeType, String content) {
        pendingExportType = mimeType;
        pendingExportContent = content;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQ_EXPORT_FILE);
    }

    private void chooseImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT_FILE);
    }

    private void importBackup(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            byte[] buffer = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buffer)) > 0) sb.append(new String(buffer, 0, n, "UTF-8"));
            db.importJson(new JSONObject(sb.toString()));
            refreshCars();
            renderCurrentTab();
            toast("备份已导入");
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    private File exportFile(String name) {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    private void share(File file, String type) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享导出文件"));
        toast("已导出：" + file.getAbsolutePath());
    }

    private boolean validOdometer(long editingId, double odometer) {
        Fuel last = db.lastFuel(energyTable(), currentCarId, editingId);
        return last == null || odometer >= last.odometer || editingId > 0;
    }

    private Car currentCar() {
        for (Car c : cars) if (c.id == currentCarId) return c;
        return cars.get(0);
    }

    private String defaultEnergyPrice() {
        Fuel last = db.lastFuel(energyTable(), currentCarId, -1);
        return last == null || last.price <= 0 ? "" : two.format(last.price);
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = dp(8);
        form.setPadding(p, p, p, p);
        return form;
    }

    private ScrollView scrollForm(View form) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, max));
        return scroll;
    }

    private EditText input(String hint, String value, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(false);
        e.setTextColor(ink);
        e.setHintTextColor(Color.rgb(168, 176, 170));
        e.setTextSize(15);
        e.setMinHeight(dp(46));
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.rgb(249, 251, 248), dp(10), Color.rgb(218, 228, 222)));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private View formHero(String title, String subtitle) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(14), dp(12), dp(14), dp(12));
        hero.setBackground(round(accentDark, dp(8), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        hero.setLayoutParams(lp);
        TextView t = label(title, 18, true);
        t.setTextColor(Color.WHITE);
        TextView s = label(subtitle, 13, false);
        s.setTextColor(Color.rgb(225, 241, 236));
        s.setPadding(0, dp(4), 0, 0);
        hero.addView(t);
        hero.addView(s);
        return hero;
    }

    private LinearLayout formSection(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(10), dp(12), dp(8));
        section.setBackground(round(Color.WHITE, dp(8), Color.rgb(224, 228, 222)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        section.setLayoutParams(lp);
        TextView heading = label(title, 15, true);
        heading.setPadding(0, 0, 0, dp(8));
        section.addView(heading);
        return section;
    }

    private View field(String title, EditText input) {
        return fieldWithHeight(title, input, dp(48));
    }

    private View tallField(String title, EditText input) {
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(2);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        return fieldWithHeight(title, input, dp(82));
    }

    private View fieldWithHeight(String title, EditText input, int height) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 12, true);
        t.setTextColor(muted);
        t.setPadding(dp(2), 0, 0, dp(5));
        wrap.addView(t);
        wrap.addView(input, new LinearLayout.LayoutParams(-1, height));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private View twoFields(View left, View right) {
        LinearLayout line = row();
        line.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
        leftLp.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -2, 1);
        rightLp.setMargins(dp(5), 0, 0, 0);
        line.addView(left, leftLp);
        line.addView(right, rightLp);
        return line;
    }

    private View checkRow(CheckBox left, CheckBox right) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(dp(2), 0, dp(2), dp(2));
        left.setBackground(round(Color.rgb(246, 250, 247), dp(10), Color.rgb(218, 228, 222)));
        right.setBackground(round(Color.rgb(246, 250, 247), dp(10), Color.rgb(218, 228, 222)));
        left.setPadding(dp(8), 0, dp(8), 0);
        right.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(-1, dp(46));
        lp1.setMargins(0, 0, 0, dp(6));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, dp(46));
        lp2.setMargins(0, 0, 0, 0);
        line.addView(left, lp1);
        line.addView(right, lp2);
        return line;
    }

    private View energyTypeButtons(EditText fuelType, EditText defaultFuel, EditText capacity) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("车辆类型", 12, true);
        title.setTextColor(muted);
        title.setPadding(dp(2), 0, 0, dp(5));
        wrap.addView(title);
        LinearLayout line = row();
        Button oil = quietButton("油车");
        Button electric = quietButton("电车");
        Runnable refresh = () -> {
            boolean ev = fuelType.getText().toString().contains("电");
            oil.setTextColor(ev ? muted : Color.WHITE);
            oil.setBackground(ev ? round(Color.rgb(246, 250, 247), dp(14), Color.rgb(218, 228, 222)) : round(accentDark, dp(14), 0));
            electric.setTextColor(ev ? Color.WHITE : muted);
            electric.setBackground(ev ? round(accentDark, dp(14), 0) : round(Color.rgb(246, 250, 247), dp(14), Color.rgb(218, 228, 222)));
        };
        oil.setOnClickListener(v -> {
            fuelType.setText("汽油");
            if (defaultFuel.getText().toString().contains("充")) defaultFuel.setText("92");
            if (num(capacity) == 60) capacity.setText("50");
            refresh.run();
        });
        electric.setOnClickListener(v -> {
            fuelType.setText("电车");
            if (defaultFuel.getText().toString().matches("92|95|98|柴油")) defaultFuel.setText("快充");
            if (num(capacity) == 50) capacity.setText("60");
            refresh.run();
        });
        line.addView(oil, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams evLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        evLp.setMargins(dp(8), 0, 0, 0);
        line.addView(electric, evLp);
        wrap.addView(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        wrap.setLayoutParams(lp);
        refresh.run();
        return wrap;
    }

    private void chooseValue(EditText target, String[] values) {
        chooseValue(target, values, null);
    }

    private void chooseValue(EditText target, String[] values, Runnable after) {
        new AlertDialog.Builder(this)
                .setTitle("请选择")
                .setItems(values, (dialog, which) -> {
                    target.setText(values[which]);
                    if (after != null) after.run();
                })
                .show();
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(ink);
        c.setChecked(checked);
        return c;
    }

    private void pickDate(EditText target) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this, (DatePicker view, int y, int m, int d) -> target.setText(String.format(Locale.CHINA, "%04d-%02d-%02d", y, m + 1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private ScrollView scroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(false);
        return s;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(8), dp(14), dp(24));
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(ink);
        v.setGravity(Gravity.START);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setLineSpacing(dp(2), 1);
        return v;
    }

    private TextView sectionTitle(String text) {
        TextView v = label(text, 20, true);
        v.setPadding(0, dp(14), 0, dp(8));
        return v;
    }

    private Button pill(String text) {
        return primaryButton(text);
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(round(accentDark, dp(14), 0));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private Button quietButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(accentDark);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(round(Color.rgb(229, 239, 235), dp(14), Color.rgb(201, 222, 215)));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private Button dangerButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.rgb(139, 45, 36));
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(round(Color.rgb(255, 238, 234), dp(14), Color.rgb(236, 182, 171)));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private Button navButton(String text, boolean active) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(active ? Color.WHITE : muted);
        b.setBackground(active ? round(accentDark, dp(14), 0) : round(surface, dp(14), Color.rgb(232, 235, 229)));
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private TextView tag(String text, int color) {
        TextView v = label(text, 12, true);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(5), dp(10), dp(5));
        v.setBackground(round(Color.rgb(241, 247, 244), dp(12), Color.rgb(210, 226, 220)));
        return v;
    }

    private View infoChip(String title, String value) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setPadding(dp(10), dp(8), dp(10), dp(8));
        chip.setBackground(round(Color.rgb(246, 248, 245), dp(8), Color.rgb(229, 234, 227)));
        TextView t = label(title, 11, false);
        t.setTextColor(muted);
        TextView v = label(value, 13, true);
        v.setPadding(0, dp(2), 0, 0);
        chip.addView(t);
        chip.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius, int strokeColor) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        if (strokeColor != 0) bg.setStroke(1, strokeColor);
        return bg;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        l.setBackground(round(Color.WHITE, dp(8), Color.rgb(224, 228, 222)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        l.setLayoutParams(lp);
        return l;
    }

    private View hero(Stats s) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(18), dp(16), dp(18), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(accentDark);
        bg.setCornerRadius(dp(8));
        h.setBackground(bg);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(6), 0, dp(12));
        h.setLayoutParams(hp);

        TextView small = label("当前平均" + consumptionName(), 13, false);
        small.setTextColor(Color.rgb(202, 229, 221));
        h.addView(small);
        TextView big = label(s.avgConsumption > 0 ? two.format(s.avgConsumption) + " " + consumptionUnit() : (isElectric(currentCar()) ? "等待第二次充满" : "等待第二次加满"), 30, true);
        big.setTextColor(Color.WHITE);
        big.setPadding(0, dp(4), 0, dp(6));
        h.addView(big);
        TextView meta = label((isElectric(currentCar()) ? "本月电费 ¥" : "本月油费 ¥") + two.format(s.monthFuelCost) + " · 总里程 " + one.format(s.totalDistance) + " km", 14, false);
        meta.setTextColor(Color.rgb(225, 241, 236));
        h.addView(meta);
        return h;
    }

    private View chartHero() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(16), dp(14), dp(16), dp(14));
        h.setBackground(round(Color.rgb(31, 117, 107), dp(8), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(12));
        h.setLayoutParams(lp);
        TextView title = label("趋势会比单次数字更诚实", 19, true);
        title.setTextColor(Color.WHITE);
        TextView sub = label(consumptionName() + "、" + (isElectric(currentCar()) ? "电价" : "油价") + "、月度费用和" + stationName() + "占比会随着记录自动更新。", 13, false);
        sub.setTextColor(Color.rgb(225, 241, 236));
        sub.setPadding(0, dp(5), 0, 0);
        h.addView(title);
        h.addView(sub);
        return h;
    }

    private View costHero(Stats s) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(18), dp(16), dp(18), dp(16));
        h.setBackground(round(Color.rgb(47, 84, 130), dp(8), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(12));
        h.setLayoutParams(lp);
        TextView title = label("真实用车成本", 14, false);
        title.setTextColor(Color.rgb(221, 233, 247));
        TextView amount = label("¥" + two.format(s.totalCost), 30, true);
        amount.setTextColor(Color.WHITE);
        TextView sub = label(s.realCostPerKm > 0 ? "平均 ¥" + two.format(s.realCostPerKm) + "/km" : "记录加油和费用后自动计算每公里成本", 14, false);
        sub.setTextColor(Color.rgb(221, 233, 247));
        h.addView(title);
        h.addView(amount);
        h.addView(sub);
        return h;
    }

    private View settingsHero() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(16), dp(14), dp(16), dp(14));
        h.setBackground(round(Color.rgb(91, 93, 86), dp(8), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(12));
        h.setLayoutParams(lp);
        TextView title = label("数据在本机，备份由你掌控", 19, true);
        title.setTextColor(Color.WHITE);
        TextView sub = label("导出时可选择保存位置，适合放到下载、网盘或文件管理器。", 13, false);
        sub.setTextColor(Color.rgb(236, 237, 232));
        sub.setPadding(0, dp(5), 0, 0);
        h.addView(title);
        h.addView(sub);
        return h;
    }

    private View settingRow(String title, String desc, Button action) {
        LinearLayout c = card();
        LinearLayout line = row();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 17, true);
        TextView d = label(desc, 13, false);
        d.setTextColor(muted);
        d.setPadding(0, dp(3), dp(8), 0);
        copy.addView(t);
        copy.addView(d);
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(action, new LinearLayout.LayoutParams(dp(138), dp(46)));
        c.addView(line);
        return c;
    }

    private View dangerPanel(String desc, Button action) {
        LinearLayout c = card();
        c.setBackground(round(Color.rgb(255, 248, 246), dp(8), Color.rgb(236, 182, 171)));
        TextView t = label("清空数据", 17, true);
        t.setTextColor(Color.rgb(139, 45, 36));
        TextView d = label(desc, 13, false);
        d.setTextColor(Color.rgb(139, 76, 68));
        d.setPadding(0, dp(4), 0, dp(12));
        c.addView(t);
        c.addView(d);
        c.addView(action, new LinearLayout.LayoutParams(-1, dp(48)));
        return c;
    }

    private View metric(String title, String value, int weight) {
        LinearLayout c = card();
        c.addView(label(title, 13, false));
        TextView val = label(value, 22, true);
        val.setPadding(0, dp(3), 0, 0);
        c.addView(val);
        if (weight > 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, weight);
            lp.setMargins(dp(3), dp(3), dp(3), dp(7));
            c.setLayoutParams(lp);
        }
        return c;
    }

    private View warn(String text) {
        TextView v = label(text, 14, true);
        v.setTextColor(Color.rgb(124, 74, 22));
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(255, 245, 220));
        bg.setCornerRadius(dp(8));
        v.setBackground(bg);
        return v;
    }

    private View help(String text) {
        TextView v = label(text, 14, false);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        return v;
    }

    private View empty(String text) {
        TextView v = label(text, 15, false);
        v.setPadding(dp(12), dp(20), dp(12), dp(20));
        return v;
    }

    private View chartCard(String title, ChartView chart) {
        LinearLayout c = card();
        c.addView(label(title, 17, true));
        c.addView(chart, new LinearLayout.LayoutParams(-1, dp(220)));
        return c;
    }

    private void clear() {
        content.removeAllViews();
    }

    private void rootAdd(View v) {
        content.addView(v, new LinearLayout.LayoutParams(-1, -1));
    }

    private String today() {
        return dayFormat.format(Calendar.getInstance().getTime());
    }

    private double num(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (Exception ex) { return 0; }
    }

    private String text(EditText e, String fallback) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard(View v) {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class Car {
        long id;
        String name, brand, plate, fuelType, defaultFuel;
        double initialOdometer, tankLiters;
    }

    static class Fuel {
        long id;
        String date, station, fuelType, note;
        double odometer, liters, amount, price, consumption, distance;
        boolean full, missed;
    }

    static class Entry {
        long id;
        String date, title, place, note;
        double odometer, amount;
    }

    static class Stats {
        double avgConsumption, lastConsumption, totalDistance, fuelCost, maintenanceCost, expenseCost, totalCost, realCostPerKm, monthFuelCost, lastOdometer;
        int recentDaysWithoutFuel;
    }

    static class FuelPoint {
        String label;
        double value;
        double secondary;
    }

    public static class Db extends SQLiteOpenHelper {
        Db(Context context) {
            super(context, "fuel_log.db", null, 2);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE cars(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,brand TEXT,plate TEXT,fuel_type TEXT,default_fuel TEXT,initial_odometer REAL,tank_liters REAL,is_default INTEGER)");
            db.execSQL("CREATE TABLE fuel_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,liters REAL,amount REAL,price REAL,station TEXT,fuel_type TEXT,is_full INTEGER,is_missed INTEGER,note TEXT)");
            db.execSQL("CREATE TABLE charging_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,liters REAL,amount REAL,price REAL,station TEXT,fuel_type TEXT,is_full INTEGER,is_missed INTEGER,note TEXT)");
            db.execSQL("CREATE TABLE maintenance_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,title TEXT,amount REAL,place TEXT,note TEXT)");
            db.execSQL("CREATE TABLE expense_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,title TEXT,amount REAL,place TEXT,note TEXT)");
            db.execSQL("CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,title TEXT,message TEXT,due_date TEXT)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("CREATE TABLE IF NOT EXISTS charging_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,liters REAL,amount REAL,price REAL,station TEXT,fuel_type TEXT,is_full INTEGER,is_missed INTEGER,note TEXT)");
            }
        }

        void ensureSeed() {
            Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cars", null);
            c.moveToFirst();
            boolean empty = c.getInt(0) == 0;
            c.close();
            if (empty) insertCar("我的车", "", "", "汽油", "92", 0, 50, true);
        }

        long insertCar(String name, String brand, String plate, String fuelType, String defaultFuel, double initialOdometer, double tankLiters, boolean isDefault) {
            ContentValues cv = new ContentValues();
            cv.put("name", name); cv.put("brand", brand); cv.put("plate", plate); cv.put("fuel_type", fuelType);
            cv.put("default_fuel", defaultFuel); cv.put("initial_odometer", initialOdometer); cv.put("tank_liters", tankLiters); cv.put("is_default", isDefault ? 1 : 0);
            return getWritableDatabase().insert("cars", null, cv);
        }

        long insert(String table, ContentValues cv) {
            return getWritableDatabase().insert(table, null, cv);
        }

        void update(String table, long id, ContentValues cv) {
            getWritableDatabase().update(table, cv, "id=?", new String[]{String.valueOf(id)});
        }

        void delete(String table, long id) {
            getWritableDatabase().delete(table, "id=?", new String[]{String.valueOf(id)});
        }

        void clearCarData(long carId) {
            SQLiteDatabase w = getWritableDatabase();
            String[] args = {String.valueOf(carId)};
            w.delete("fuel_records", "car_id=?", args);
            w.delete("charging_records", "car_id=?", args);
            w.delete("maintenance_records", "car_id=?", args);
            w.delete("expense_records", "car_id=?", args);
            w.delete("reminders", "car_id=?", args);
        }

        List<Car> cars() {
            List<Car> list = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM cars ORDER BY id", null);
            while (c.moveToNext()) {
                Car car = new Car();
                car.id = c.getLong(c.getColumnIndexOrThrow("id"));
                car.name = s(c, "name"); car.brand = s(c, "brand"); car.plate = s(c, "plate");
                car.fuelType = s(c, "fuel_type"); car.defaultFuel = s(c, "default_fuel");
                car.initialOdometer = d(c, "initial_odometer"); car.tankLiters = d(c, "tank_liters");
                list.add(car);
            }
            c.close();
            return list;
        }

        Fuel lastFuel(String table, long carId, long excludeId) {
            String sql = excludeId > 0 ? "SELECT * FROM " + table + " WHERE car_id=? AND id<>? ORDER BY odometer DESC,id DESC LIMIT 1" : "SELECT * FROM " + table + " WHERE car_id=? ORDER BY odometer DESC,id DESC LIMIT 1";
            String[] args = excludeId > 0 ? new String[]{String.valueOf(carId), String.valueOf(excludeId)} : new String[]{String.valueOf(carId)};
            Cursor c = getReadableDatabase().rawQuery(sql, args);
            Fuel f = c.moveToFirst() ? fuel(c) : null;
            c.close();
            return f;
        }

        List<Fuel> fuels(String table, long carId, int limit) {
            Map<Long, FuelCalc> calc = consumptionMap(table, carId);
            List<Fuel> list = new ArrayList<>();
            String sql = "SELECT * FROM " + table + " WHERE car_id=? ORDER BY date DESC,odometer DESC,id DESC" + (limit > 0 ? " LIMIT " + limit : "");
            Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(carId)});
            while (c.moveToNext()) {
                Fuel f = fuel(c);
                FuelCalc fc = calc.get(f.id);
                if (fc != null) {
                    f.consumption = fc.consumption;
                    f.distance = fc.distance;
                }
                list.add(f);
            }
            c.close();
            return list;
        }

        List<Entry> entries(String table, long carId, int limit) {
            List<Entry> list = new ArrayList<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + table + " WHERE car_id=? ORDER BY date DESC,id DESC" + (limit > 0 ? " LIMIT " + limit : ""), new String[]{String.valueOf(carId)});
            while (c.moveToNext()) list.add(entry(c));
            c.close();
            return list;
        }

        Stats stats(String table, long carId) {
            Stats st = new Stats();
            List<Fuel> fuels = fuels(table, carId, 0);
            double sumCons = 0;
            int consCount = 0;
            for (Fuel f : fuels) {
                st.fuelCost += f.amount;
                if (f.consumption > 0) {
                    sumCons += f.consumption;
                    consCount++;
                    if (st.lastConsumption == 0) st.lastConsumption = f.consumption;
                }
                if (st.lastOdometer == 0 || f.odometer > st.lastOdometer) st.lastOdometer = f.odometer;
                if (f.date != null && f.date.startsWith(monthPrefix())) st.monthFuelCost += f.amount;
            }
            if (consCount > 0) st.avgConsumption = sumCons / consCount;
            CarBase base = carBase(carId);
            if (st.lastOdometer > base.initialOdometer) st.totalDistance = st.lastOdometer - base.initialOdometer;
            st.maintenanceCost = sumAmount("maintenance_records", carId);
            st.expenseCost = sumAmount("expense_records", carId);
            st.totalCost = st.fuelCost + st.maintenanceCost + st.expenseCost;
            if (st.totalDistance > 0) st.realCostPerKm = st.totalCost / st.totalDistance;
            if (!fuels.isEmpty()) st.recentDaysWithoutFuel = daysSince(fuels.get(0).date);
            return st;
        }

        List<FuelPoint> fuelPoints(String table, long carId) {
            List<FuelPoint> list = new ArrayList<>();
            List<Fuel> fuels = fuels(table, carId, 0);
            for (int i = fuels.size() - 1; i >= 0; i--) {
                Fuel f = fuels.get(i);
                FuelPoint p = new FuelPoint();
                p.label = f.date == null ? "" : f.date.substring(Math.max(0, f.date.length() - 5));
                p.value = f.consumption;
                p.secondary = f.price;
                list.add(p);
            }
            return list;
        }

        List<FuelPoint> monthPoints(String energyTable, long carId, String mode) {
            Map<String, Double> map = new java.util.TreeMap<>();
            addMonthCosts(map, energyTable, carId, "amount");
            if ("all".equals(mode)) {
                addMonthCosts(map, "maintenance_records", carId, "amount");
                addMonthCosts(map, "expense_records", carId, "amount");
            }
            List<FuelPoint> points = new ArrayList<>();
            for (String k : map.keySet()) {
                FuelPoint p = new FuelPoint();
                p.label = k.substring(5);
                p.value = map.get(k);
                points.add(p);
            }
            return points;
        }

        List<FuelPoint> stationPoints(String table, long carId) {
            Map<String, Double> map = new HashMap<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT station,SUM(amount) total FROM " + table + " WHERE car_id=? GROUP BY station", new String[]{String.valueOf(carId)});
            while (c.moveToNext()) {
                String station = c.getString(0);
                if (station == null || station.trim().isEmpty()) station = "未填写";
                map.put(station, c.getDouble(1));
            }
            c.close();
            List<FuelPoint> list = new ArrayList<>();
            for (String k : map.keySet()) {
                FuelPoint p = new FuelPoint();
                p.label = k;
                p.value = map.get(k);
                list.add(p);
            }
            return list;
        }

        List<Entry> expenseSummary(long carId) {
            Map<String, Double> map = new HashMap<>();
            addSummary(map, "maintenance_records", carId);
            addSummary(map, "expense_records", carId);
            List<Entry> list = new ArrayList<>();
            for (String k : map.keySet()) {
                Entry e = new Entry();
                e.title = k;
                e.amount = map.get(k);
                list.add(e);
            }
            return list;
        }

        JSONObject exportJson() throws Exception {
            JSONObject root = new JSONObject();
            root.put("cars", tableJson("cars", null));
            root.put("fuel_records", tableJson("fuel_records", null));
            root.put("charging_records", tableJson("charging_records", null));
            root.put("maintenance_records", tableJson("maintenance_records", null));
            root.put("expense_records", tableJson("expense_records", null));
            root.put("reminders", tableJson("reminders", null));
            return root;
        }

        String exportEnergyCsv(String table, long carId) {
            boolean charging = "charging_records".equals(table);
            StringBuilder sb = new StringBuilder();
            sb.append("日期,当前总里程,").append(charging ? "充电量(kWh),电费(元),电价(元/kWh),充电站/位置,充电方式" : "加油量(L),金额(元),油价(元/L),加油站,油品").append(",是否加满/充满,是否漏记补录,备注\n");
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + table + " WHERE car_id=? ORDER BY date,id", new String[]{String.valueOf(carId)});
            while (c.moveToNext()) {
                sb.append(csv(s(c, "date"))).append(',')
                        .append(d(c, "odometer")).append(',')
                        .append(d(c, "liters")).append(',')
                        .append(d(c, "amount")).append(',')
                        .append(d(c, "price")).append(',')
                        .append(csv(s(c, "station"))).append(',')
                        .append(csv(s(c, "fuel_type"))).append(',')
                        .append(i(c, "is_full")).append(',')
                        .append(i(c, "is_missed")).append(',')
                        .append(csv(s(c, "note"))).append('\n');
            }
            c.close();
            return sb.toString();
        }

        void importJson(JSONObject root) throws Exception {
            SQLiteDatabase w = getWritableDatabase();
            w.beginTransaction();
            try {
                String[] tables = {"cars", "fuel_records", "charging_records", "maintenance_records", "expense_records", "reminders"};
                for (String table : tables) w.delete(table, null, null);
                importTable(w, root, "cars", new String[]{"id", "name", "brand", "plate", "fuel_type", "default_fuel", "initial_odometer", "tank_liters", "is_default"});
                importTable(w, root, "fuel_records", new String[]{"id", "car_id", "date", "odometer", "liters", "amount", "price", "station", "fuel_type", "is_full", "is_missed", "note"});
                importTable(w, root, "charging_records", new String[]{"id", "car_id", "date", "odometer", "liters", "amount", "price", "station", "fuel_type", "is_full", "is_missed", "note"});
                importTable(w, root, "maintenance_records", new String[]{"id", "car_id", "date", "odometer", "title", "amount", "place", "note"});
                importTable(w, root, "expense_records", new String[]{"id", "car_id", "date", "odometer", "title", "amount", "place", "note"});
                importTable(w, root, "reminders", new String[]{"id", "car_id", "title", "message", "due_date"});
                w.setTransactionSuccessful();
            } finally {
                w.endTransaction();
            }
            ensureSeed();
        }

        private void importTable(SQLiteDatabase w, JSONObject root, String table, String[] columns) throws Exception {
            if (!root.has(table)) return;
            JSONArray arr = root.getJSONArray(table);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ContentValues cv = new ContentValues();
                for (String col : columns) {
                    if (!o.has(col) || o.isNull(col)) continue;
                    Object value = o.get(col);
                    if (value instanceof Integer || value instanceof Long) cv.put(col, ((Number) value).longValue());
                    else if (value instanceof Number) cv.put(col, ((Number) value).doubleValue());
                    else cv.put(col, String.valueOf(value));
                }
                w.insert(table, null, cv);
            }
        }

        private JSONArray tableJson(String table, String where) throws Exception {
            JSONArray arr = new JSONArray();
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + table + (where == null ? "" : " WHERE " + where), null);
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                for (int idx = 0; idx < c.getColumnCount(); idx++) {
                    int type = c.getType(idx);
                    String name = c.getColumnName(idx);
                    if (type == Cursor.FIELD_TYPE_INTEGER) o.put(name, c.getLong(idx));
                    else if (type == Cursor.FIELD_TYPE_FLOAT) o.put(name, c.getDouble(idx));
                    else o.put(name, c.getString(idx));
                }
                arr.put(o);
            }
            c.close();
            return arr;
        }

        private Map<Long, FuelCalc> consumptionMap(String table, long carId) {
            Map<Long, FuelCalc> map = new HashMap<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + table + " WHERE car_id=? ORDER BY odometer ASC,id ASC", new String[]{String.valueOf(carId)});
            Fuel lastFull = null;
            double pendingLiters = 0;
            while (c.moveToNext()) {
                Fuel f = fuel(c);
                if (f.missed) {
                    lastFull = f.full ? f : null;
                    pendingLiters = 0;
                    continue;
                }
                pendingLiters += f.liters;
                if (f.full) {
                    if (lastFull != null && f.odometer > lastFull.odometer && pendingLiters > 0) {
                        FuelCalc fc = new FuelCalc();
                        fc.distance = f.odometer - lastFull.odometer;
                        fc.consumption = pendingLiters / fc.distance * 100;
                        map.put(f.id, fc);
                    }
                    lastFull = f;
                    pendingLiters = 0;
                }
            }
            c.close();
            return map;
        }

        private void addMonthCosts(Map<String, Double> map, String table, long carId, String col) {
            Cursor c = getReadableDatabase().rawQuery("SELECT substr(date,1,7) m,SUM(" + col + ") total FROM " + table + " WHERE car_id=? GROUP BY m ORDER BY m", new String[]{String.valueOf(carId)});
            while (c.moveToNext()) {
                String m = c.getString(0);
                if (m != null) map.put(m, map.containsKey(m) ? map.get(m) + c.getDouble(1) : c.getDouble(1));
            }
            c.close();
        }

        private void addSummary(Map<String, Double> map, String table, long carId) {
            Cursor c = getReadableDatabase().rawQuery("SELECT title,SUM(amount) FROM " + table + " WHERE car_id=? GROUP BY title", new String[]{String.valueOf(carId)});
            while (c.moveToNext()) {
                String title = c.getString(0);
                if (title == null || title.isEmpty()) title = "其他";
                double value = c.getDouble(1);
                map.put(title, map.containsKey(title) ? map.get(title) + value : value);
            }
            c.close();
        }

        private double sumAmount(String table, long carId) {
            Cursor c = getReadableDatabase().rawQuery("SELECT SUM(amount) FROM " + table + " WHERE car_id=?", new String[]{String.valueOf(carId)});
            c.moveToFirst();
            double value = c.isNull(0) ? 0 : c.getDouble(0);
            c.close();
            return value;
        }

        private CarBase carBase(long carId) {
            Cursor c = getReadableDatabase().rawQuery("SELECT initial_odometer FROM cars WHERE id=?", new String[]{String.valueOf(carId)});
            CarBase b = new CarBase();
            if (c.moveToFirst()) b.initialOdometer = c.getDouble(0);
            c.close();
            return b;
        }

        private Fuel fuel(Cursor c) {
            Fuel f = new Fuel();
            f.id = c.getLong(c.getColumnIndexOrThrow("id"));
            f.date = s(c, "date"); f.odometer = d(c, "odometer"); f.liters = d(c, "liters"); f.amount = d(c, "amount"); f.price = d(c, "price");
            f.station = s(c, "station"); f.fuelType = s(c, "fuel_type"); f.full = i(c, "is_full") == 1; f.missed = i(c, "is_missed") == 1; f.note = s(c, "note");
            return f;
        }

        private Entry entry(Cursor c) {
            Entry e = new Entry();
            e.id = c.getLong(c.getColumnIndexOrThrow("id"));
            e.date = s(c, "date"); e.odometer = d(c, "odometer"); e.title = s(c, "title"); e.amount = d(c, "amount"); e.place = s(c, "place"); e.note = s(c, "note");
            return e;
        }

        private String monthPrefix() {
            return new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Calendar.getInstance().getTime());
        }

        private int daysSince(String date) {
            try {
                long t = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(date).getTime();
                return (int) ((System.currentTimeMillis() - t) / 86400000L);
            } catch (Exception e) {
                return 0;
            }
        }

        private String csv(String s) {
            if (s == null) return "";
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }

        private static String s(Cursor c, String col) {
            int idx = c.getColumnIndexOrThrow(col);
            return c.isNull(idx) ? "" : c.getString(idx);
        }

        private static double d(Cursor c, String col) {
            int idx = c.getColumnIndexOrThrow(col);
            return c.isNull(idx) ? 0 : c.getDouble(idx);
        }

        private static int i(Cursor c, String col) {
            int idx = c.getColumnIndexOrThrow(col);
            return c.isNull(idx) ? 0 : c.getInt(idx);
        }
    }

    static class FuelCalc {
        double consumption, distance;
    }

    static class CarBase {
        double initialOdometer;
    }

    public class ChartView extends View {
        static final int MODE_CONSUMPTION = 1;
        static final int MODE_PRICE = 2;
        static final int MODE_BAR = 3;
        static final int MODE_PIE = 4;
        private final List<FuelPoint> points;
        private final int mode;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChartView(Context context, List<FuelPoint> points, int mode) {
            super(context);
            this.points = points;
            this.mode = mode;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            p.setColor(Color.rgb(250, 248, 242));
            canvas.drawRoundRect(new RectF(0, dp(8), w, h), dp(8), dp(8), p);
            if (points.isEmpty()) {
                p.setColor(Color.rgb(117, 112, 101));
                p.setTextSize(dp(14));
                canvas.drawText("暂无足够数据", dp(18), h / 2f, p);
                return;
            }
            if (mode == MODE_PIE) drawPie(canvas, w, h);
            else if (mode == MODE_BAR) drawBar(canvas, w, h);
            else drawLine(canvas, w, h);
        }

        private void drawLine(Canvas c, int w, int h) {
            int left = dp(36), top = dp(22), right = w - dp(18), bottom = h - dp(34);
            double max = 0;
            List<FuelPoint> usable = new ArrayList<>();
            for (FuelPoint fp : points) {
                double v = mode == MODE_PRICE ? fp.secondary : fp.value;
                if (v > 0) {
                    FuelPoint copy = new FuelPoint();
                    copy.label = fp.label; copy.value = v;
                    usable.add(copy);
                    max = Math.max(max, v);
                }
            }
            if (usable.isEmpty()) return;
            p.setColor(Color.rgb(218, 211, 196));
            p.setStrokeWidth(1);
            c.drawLine(left, bottom, right, bottom, p);
            c.drawLine(left, top, left, bottom, p);
            Path path = new Path();
            for (int i = 0; i < usable.size(); i++) {
                float x = usable.size() == 1 ? left : left + (right - left) * i / (float) (usable.size() - 1);
                float y = bottom - (float) (usable.get(i).value / max) * (bottom - top);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                p.setColor(accent);
                c.drawCircle(x, y, dp(3), p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            p.setColor(accent);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(dp(11));
            p.setColor(ink);
            c.drawText(new DecimalFormat("0.##").format(max), dp(4), top + dp(4), p);
            c.drawText(usable.get(usable.size() - 1).label, Math.max(left, right - dp(38)), h - dp(10), p);
        }

        private void drawBar(Canvas c, int w, int h) {
            int left = dp(30), top = dp(22), bottom = h - dp(34);
            double max = 0;
            for (FuelPoint fp : points) max = Math.max(max, fp.value);
            if (max <= 0) return;
            int count = Math.min(points.size(), 8);
            int start = Math.max(0, points.size() - count);
            float slot = (w - left - dp(14)) / (float) count;
            for (int i = 0; i < count; i++) {
                FuelPoint fp = points.get(start + i);
                float barH = (float) (fp.value / max) * (bottom - top);
                float x = left + i * slot + dp(5);
                p.setColor(Color.rgb(35, 108, 104));
                c.drawRoundRect(new RectF(x, bottom - barH, x + slot - dp(10), bottom), dp(4), dp(4), p);
                p.setColor(ink);
                p.setTextSize(dp(10));
                c.drawText(fp.label, x, h - dp(10), p);
            }
        }

        private void drawPie(Canvas c, int w, int h) {
            double total = 0;
            for (FuelPoint fp : points) total += fp.value;
            if (total <= 0) return;
            RectF oval = new RectF(dp(20), dp(30), dp(150), dp(160));
            int[] colors = {Color.rgb(35,108,104), Color.rgb(199,80,63), Color.rgb(47,84,130), Color.rgb(210,154,69), Color.rgb(91,93,86)};
            float start = -90;
            for (int i = 0; i < points.size(); i++) {
                FuelPoint fp = points.get(i);
                p.setColor(colors[i % colors.length]);
                float sweep = (float) (fp.value / total * 360);
                c.drawArc(oval, start, sweep, true, p);
                start += sweep;
                p.setTextSize(dp(12));
                c.drawText(fp.label, dp(170), dp(48 + i * 24), p);
            }
        }
    }
}
