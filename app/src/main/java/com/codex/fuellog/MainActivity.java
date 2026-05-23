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
import android.content.SharedPreferences;
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
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
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
    private Spinner carSpinner;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private final List<Car> cars = new ArrayList<>();
    private long currentCarId = -1;
    private int selectedTab = 0;
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
        screenTitle = label("油耗记录", 24, true);
        screenSubtitle = label("记录每一次加油，长期看清真实成本", 13, false);
        screenSubtitle.setTextColor(muted);
        titles.addView(screenTitle);
        titles.addView(screenSubtitle);
        titleBar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button addFuel = primaryButton("+ 加油");
        addFuel.setOnClickListener(v -> showFuelDialog(null));
        titleBar.addView(addFuel, new LinearLayout.LayoutParams(dp(104), dp(44)));
        top.addView(titleBar);

        LinearLayout carLine = new LinearLayout(this);
        carLine.setGravity(Gravity.CENTER_VERTICAL);
        carLine.setPadding(0, dp(12), 0, 0);
        TextView carLabel = label("当前车辆", 13, false);
        carLabel.setTextColor(muted);
        carLine.addView(carLabel, new LinearLayout.LayoutParams(dp(76), -2));
        carSpinner = new Spinner(this);
        carSpinner.setPadding(0, 0, 0, 0);
        carSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < cars.size()) {
                    long next = cars.get(position).id;
                    if (next != currentCarId) {
                        currentCarId = next;
                        saveDefaultCar(next);
                        renderCurrentTab();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        carLine.addView(carSpinner, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button cars = quietButton("管理");
        cars.setOnClickListener(v -> showCarManager());
        carLine.addView(cars, new LinearLayout.LayoutParams(dp(70), dp(40)));
        top.addView(carLine);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(8), dp(7), dp(8), dp(8));
        bottomNav.setBackgroundColor(surface);
        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(68)));
        buildBottomNav();
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
        List<String> names = new ArrayList<>();
        long saved = getPreferences(MODE_PRIVATE).getLong("default_car", cars.get(0).id);
        int selected = 0;
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            names.add(c.name + (c.plate.isEmpty() ? "" : " · " + c.plate));
            if (c.id == saved) selected = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        carSpinner.setAdapter(adapter);
        carSpinner.setSelection(selected);
        currentCarId = cars.get(selected).id;
    }

    private void saveDefaultCar(long id) {
        getPreferences(MODE_PRIVATE).edit().putLong("default_car", id).apply();
    }

    private void showDashboard() {
        selectedTab = 0;
        setHeader("油耗记录", "一眼看清油耗、里程和用车成本");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        Stats s = db.stats(currentCarId);
        box.addView(hero(s));
        LinearLayout quick = row();
        Button fuel = primaryButton("+ 加油");
        fuel.setOnClickListener(v -> showFuelDialog(null));
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
        row1.addView(metric("平均油耗", s.avgConsumption > 0 ? two.format(s.avgConsumption) + " L/100km" : "暂无", 1));
        row1.addView(metric("最近油耗", s.lastConsumption > 0 ? two.format(s.lastConsumption) + " L/100km" : "暂无", 1));
        grid.addView(row1);
        LinearLayout row2 = row();
        row2.addView(metric("本月油费", "¥" + two.format(s.monthFuelCost), 1));
        row2.addView(metric("真实每公里", s.realCostPerKm > 0 ? "¥" + two.format(s.realCostPerKm) : "暂无", 1));
        grid.addView(row2);
        LinearLayout row3 = row();
        row3.addView(metric("总里程", one.format(s.totalDistance) + " km", 1));
        row3.addView(metric("总支出", "¥" + two.format(s.totalCost), 1));
        grid.addView(row3);

        if (s.avgConsumption > 0 && s.lastConsumption > s.avgConsumption * 1.25) {
            box.addView(warn("最近一次油耗高于平均值 25% 以上，可检查胎压、路况或数据输入。"));
        }
        if (s.recentDaysWithoutFuel > 30) {
            box.addView(warn("超过 30 天没有加油记录，若最近加过油可以补记。"));
        }

        box.addView(sectionTitle("最近加油"));
        List<Fuel> fuels = db.fuels(currentCarId, 3);
        if (fuels.isEmpty()) {
            box.addView(empty("还没有加油记录。点“新增”开始记录第一次加满，之后就能计算油耗。"));
        } else {
            for (Fuel f : fuels) box.addView(fuelRow(f, false));
        }
        rootAdd(scroll);
    }

    private void showAdd() {
        showFuelDialog(null);
    }

    private void showRecords() {
        selectedTab = 1;
        setHeader("记录", "查看、编辑或长按删除历史记录");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        box.addView(sectionTitle("加油记录"));
        List<Fuel> fuels = db.fuels(currentCarId, 0);
        if (fuels.isEmpty()) box.addView(empty("暂无加油记录"));
        for (Fuel f : fuels) box.addView(fuelRow(f, true));
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
        box.addView(sectionTitle("数据可视化"));
        List<FuelPoint> points = db.fuelPoints(currentCarId);
        box.addView(chartCard("油耗趋势 L/100km", new ChartView(this, points, ChartView.MODE_CONSUMPTION)));
        box.addView(chartCard("油价趋势 元/L", new ChartView(this, points, ChartView.MODE_PRICE)));
        box.addView(chartCard("月度油费", new ChartView(this, db.monthPoints(currentCarId, "fuel"), ChartView.MODE_BAR)));
        box.addView(chartCard("月度总用车成本", new ChartView(this, db.monthPoints(currentCarId, "all"), ChartView.MODE_BAR)));
        box.addView(chartCard("加油站费用占比", new ChartView(this, db.stationPoints(currentCarId), ChartView.MODE_PIE)));
        rootAdd(scroll);
    }

    private void showCosts() {
        selectedTab = 3;
        setHeader("费用", "把加油、保养和其他支出合在一起看");
        clear();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        scroll.addView(box);
        Stats s = db.stats(currentCarId);
        box.addView(sectionTitle("费用分析"));
        LinearLayout row1 = row();
        row1.addView(metric("加油", "¥" + two.format(s.fuelCost), 1));
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
        box.addView(sectionTitle("设置与数据"));
        Button carsButton = primaryButton("管理车辆");
        carsButton.setOnClickListener(v -> showCarManager());
        Button exportJson = quietButton("导出 JSON 备份");
        exportJson.setOnClickListener(v -> exportJson());
        Button exportCsv = quietButton("导出 CSV 表格");
        exportCsv.setOnClickListener(v -> exportCsv());
        Button reminder = quietButton("创建本地提醒");
        reminder.setOnClickListener(v -> showReminderDialog());
        Button clear = quietButton("清空当前车辆数据");
        clear.setOnClickListener(v -> confirmClear());
        box.addView(carsButton, new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(exportJson, new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(exportCsv, new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(reminder, new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(clear, new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(help("数据保存在手机本地 SQLite 中。导出的 JSON 可作为完整备份，CSV 可直接用表格软件查看。"));
        rootAdd(scroll);
    }

    private View fuelRow(Fuel f, boolean editable) {
        LinearLayout card = card();
        TextView title = label(f.date + " · " + one.format(f.odometer) + " km", 17, true);
        card.addView(title);
        String status = f.full ? "加满" : "未加满";
        if (f.missed) status += " · 漏记";
        card.addView(label(one.format(f.liters) + " L · ¥" + two.format(f.amount) + " · " + two.format(f.price) + " 元/L · " + status, 14, false));
        if (f.consumption > 0) {
            card.addView(label("区间 " + one.format(f.distance) + " km · 油耗 " + two.format(f.consumption) + " L/100km", 14, false));
        } else {
            card.addView(label("本条不单独计算油耗", 14, false));
        }
        if (!f.station.isEmpty() || !f.note.isEmpty()) card.addView(label(f.station + (f.note.isEmpty() ? "" : " · " + f.note), 13, false));
        if (editable) {
            card.setOnClickListener(v -> showFuelDialog(f));
            card.setOnLongClickListener(v -> {
                confirmDelete("fuel_records", f.id);
                return true;
            });
        }
        return card;
    }

    private View entryRow(String table, Entry e) {
        LinearLayout card = card();
        card.addView(label(e.date + " · " + e.title, 17, true));
        card.addView(label("¥" + two.format(e.amount) + " · " + one.format(e.odometer) + " km", 14, false));
        if (!e.note.isEmpty()) card.addView(label(e.note, 13, false));
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

    private void showFuelDialog(Fuel existing) {
        LinearLayout form = dialogForm();
        EditText date = input("日期 yyyy-MM-dd", existing == null ? today() : existing.date, false);
        EditText odo = input("当前总里程 km", existing == null ? "" : one.format(existing.odometer), true);
        EditText liters = input("加油量 L", existing == null ? "" : one.format(existing.liters), true);
        EditText amount = input("金额 元", existing == null ? "" : two.format(existing.amount), true);
        EditText price = input("油价 元/L", existing == null ? defaultFuelPrice() : two.format(existing.price), true);
        EditText station = input("加油站", existing == null ? "" : existing.station, false);
        EditText fuelType = input("油品", existing == null ? currentCar().defaultFuel : existing.fuelType, false);
        CheckBox full = check("本次加满", existing == null || existing.full);
        CheckBox missed = check("漏记后补录/跳过本区间油耗", existing != null && existing.missed);
        EditText note = input("备注", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));
        form.addView(date); form.addView(odo); form.addView(liters); form.addView(amount); form.addView(price);
        form.addView(station); form.addView(fuelType); form.addView(full); form.addView(missed); form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增加油" : "编辑加油")
                .setView(form)
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

    private void showMaintenanceDialog(Entry existing) {
        LinearLayout form = dialogForm();
        EditText date = input("日期 yyyy-MM-dd", existing == null ? today() : existing.date, false);
        EditText odo = input("当前总里程 km", existing == null ? "" : one.format(existing.odometer), true);
        EditText title = input("保养类型", existing == null ? "机油/机滤" : existing.title, false);
        EditText amount = input("金额 元", existing == null ? "" : two.format(existing.amount), true);
        EditText place = input("门店", existing == null ? "" : existing.place, false);
        EditText note = input("备注", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));
        form.addView(date); form.addView(odo); form.addView(title); form.addView(amount); form.addView(place); form.addView(note);
        entryDialog(existing, "maintenance_records", "保养记录", form, date, odo, title, amount, place, note);
    }

    private void showExpenseDialog(Entry existing) {
        LinearLayout form = dialogForm();
        EditText date = input("日期 yyyy-MM-dd", existing == null ? today() : existing.date, false);
        EditText odo = input("当前总里程 km", existing == null ? "" : one.format(existing.odometer), true);
        EditText title = input("费用类型", existing == null ? "停车费" : existing.title, false);
        EditText amount = input("金额 元", existing == null ? "" : two.format(existing.amount), true);
        EditText place = input("地点/商户", existing == null ? "" : existing.place, false);
        EditText note = input("备注", existing == null ? "" : existing.note, false);
        date.setOnClickListener(v -> pickDate(date));
        form.addView(date); form.addView(odo); form.addView(title); form.addView(amount); form.addView(place); form.addView(note);
        entryDialog(existing, "expense_records", "费用记录", form, date, odo, title, amount, place, note);
    }

    private void entryDialog(Entry existing, String table, String titleText, LinearLayout form, EditText date, EditText odo, EditText title, EditText amount, EditText place, EditText note) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增" + titleText : "编辑" + titleText)
                .setView(form)
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
        for (Car c : db.cars()) {
            TextView row = label(c.name + " · " + c.fuelType + " · " + c.defaultFuel, 16, true);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            row.setOnClickListener(v -> showCarDialog(c));
            list.addView(row);
        }
        Button add = pill("添加车辆");
        add.setOnClickListener(v -> showCarDialog(null));
        list.addView(add);
        new AlertDialog.Builder(this).setTitle("车辆管理").setView(list).setNegativeButton("关闭", null).show();
    }

    private void showCarDialog(Car c) {
        LinearLayout form = dialogForm();
        EditText name = input("车辆名称", c == null ? "我的车" : c.name, false);
        EditText brand = input("品牌/型号", c == null ? "" : c.brand, false);
        EditText plate = input("车牌号", c == null ? "" : c.plate, false);
        EditText fuelType = input("燃油类型", c == null ? "汽油" : c.fuelType, false);
        EditText defaultFuel = input("默认油品", c == null ? "92" : c.defaultFuel, false);
        EditText initial = input("初始里程 km", c == null ? "0" : one.format(c.initialOdometer), true);
        EditText tank = input("油箱容量 L", c == null ? "50" : one.format(c.tankLiters), true);
        form.addView(name); form.addView(brand); form.addView(plate); form.addView(fuelType); form.addView(defaultFuel); form.addView(initial); form.addView(tank);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(c == null ? "添加车辆" : "编辑车辆")
                .setView(form)
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
            File file = exportFile("fuel-log-backup.json");
            FileWriter writer = new FileWriter(file);
            writer.write(db.exportJson().toString(2));
            writer.close();
            share(file, "application/json");
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private void exportCsv() {
        try {
            File file = exportFile("fuel-records.csv");
            FileWriter writer = new FileWriter(file);
            writer.write(db.exportFuelCsv(currentCarId));
            writer.close();
            share(file, "text/csv");
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
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
        Fuel last = db.lastFuel(currentCarId, editingId);
        return last == null || odometer >= last.odometer || editingId > 0;
    }

    private Car currentCar() {
        for (Car c : cars) if (c.id == currentCarId) return c;
        return cars.get(0);
    }

    private String defaultFuelPrice() {
        Fuel last = db.lastFuel(currentCarId, -1);
        return last == null || last.price <= 0 ? "" : two.format(last.price);
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = dp(10);
        form.setPadding(p, p, p, p);
        return form;
    }

    private EditText input(String hint, String value, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(false);
        e.setTextColor(ink);
        e.setHintTextColor(Color.rgb(117, 112, 101));
        e.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
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
        b.setBackgroundColor(accentDark);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private Button quietButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(accentDark);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(229, 239, 235));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private Button navButton(String text, boolean active) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(active ? Color.WHITE : muted);
        b.setBackgroundColor(active ? accentDark : surface);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, Color.rgb(224, 218, 204));
        l.setBackground(bg);
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

        TextView small = label("当前平均油耗", 13, false);
        small.setTextColor(Color.rgb(202, 229, 221));
        h.addView(small);
        TextView big = label(s.avgConsumption > 0 ? two.format(s.avgConsumption) + " L/100km" : "等待第二次加满", 30, true);
        big.setTextColor(Color.WHITE);
        big.setPadding(0, dp(4), 0, dp(6));
        h.addView(big);
        TextView meta = label("本月油费 ¥" + two.format(s.monthFuelCost) + " · 总里程 " + one.format(s.totalDistance) + " km", 14, false);
        meta.setTextColor(Color.rgb(225, 241, 236));
        h.addView(meta);
        return h;
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
            super(context, "fuel_log.db", null, 1);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE cars(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,brand TEXT,plate TEXT,fuel_type TEXT,default_fuel TEXT,initial_odometer REAL,tank_liters REAL,is_default INTEGER)");
            db.execSQL("CREATE TABLE fuel_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,liters REAL,amount REAL,price REAL,station TEXT,fuel_type TEXT,is_full INTEGER,is_missed INTEGER,note TEXT)");
            db.execSQL("CREATE TABLE maintenance_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,title TEXT,amount REAL,place TEXT,note TEXT)");
            db.execSQL("CREATE TABLE expense_records(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,date TEXT,odometer REAL,title TEXT,amount REAL,place TEXT,note TEXT)");
            db.execSQL("CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT,car_id INTEGER,title TEXT,message TEXT,due_date TEXT)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

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

        Fuel lastFuel(long carId, long excludeId) {
            String sql = excludeId > 0 ? "SELECT * FROM fuel_records WHERE car_id=? AND id<>? ORDER BY odometer DESC,id DESC LIMIT 1" : "SELECT * FROM fuel_records WHERE car_id=? ORDER BY odometer DESC,id DESC LIMIT 1";
            String[] args = excludeId > 0 ? new String[]{String.valueOf(carId), String.valueOf(excludeId)} : new String[]{String.valueOf(carId)};
            Cursor c = getReadableDatabase().rawQuery(sql, args);
            Fuel f = c.moveToFirst() ? fuel(c) : null;
            c.close();
            return f;
        }

        List<Fuel> fuels(long carId, int limit) {
            Map<Long, FuelCalc> calc = consumptionMap(carId);
            List<Fuel> list = new ArrayList<>();
            String sql = "SELECT * FROM fuel_records WHERE car_id=? ORDER BY date DESC,odometer DESC,id DESC" + (limit > 0 ? " LIMIT " + limit : "");
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

        Stats stats(long carId) {
            Stats st = new Stats();
            List<Fuel> fuels = fuels(carId, 0);
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

        List<FuelPoint> fuelPoints(long carId) {
            List<FuelPoint> list = new ArrayList<>();
            List<Fuel> fuels = fuels(carId, 0);
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

        List<FuelPoint> monthPoints(long carId, String mode) {
            Map<String, Double> map = new java.util.TreeMap<>();
            addMonthCosts(map, "fuel_records", carId, "amount");
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

        List<FuelPoint> stationPoints(long carId) {
            Map<String, Double> map = new HashMap<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT station,SUM(amount) total FROM fuel_records WHERE car_id=? GROUP BY station", new String[]{String.valueOf(carId)});
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
            root.put("maintenance_records", tableJson("maintenance_records", null));
            root.put("expense_records", tableJson("expense_records", null));
            root.put("reminders", tableJson("reminders", null));
            return root;
        }

        String exportFuelCsv(long carId) {
            StringBuilder sb = new StringBuilder("date,odometer,liters,amount,price,station,fuel_type,is_full,is_missed,note\n");
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM fuel_records WHERE car_id=? ORDER BY date,id", new String[]{String.valueOf(carId)});
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

        private Map<Long, FuelCalc> consumptionMap(long carId) {
            Map<Long, FuelCalc> map = new HashMap<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM fuel_records WHERE car_id=? ORDER BY odometer ASC,id ASC", new String[]{String.valueOf(carId)});
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
