import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

// Перечисления и классы данных
enum UserRole {
    ROLE_ADMIN(1),
    ROLE_USER(2);

    private final int value;

    UserRole(int value) {
        this.value = value;
    }

    public static UserRole fromValue(int value) {
        for (UserRole role : values()) {
            if (role.value == value) {
                return role;
            }
        }
        throw new IllegalArgumentException("Недопустимое значение роли: " + value);
    }
}

class Order implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    String name;
    String brand;
    String status;
    String phone;
    float price;
    String fullName;
    String dateAppointment;
    String dateIssue;

    public Order() {}

    @Override
    public String toString() {
        return String.format("%s | Марка: %s | ФИО заказчика: %s | Цена: %.2f | Статус: %s | Телефон заказчика: %s | Дата приёма: %s | Дата выдачи: %s",
                name, brand, fullName, price, status, phone, dateAppointment, dateIssue);
    }
}

class User implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    String login;
    String password;
    UserRole role;

    public User(String login, String password, UserRole role) {
        this.login = login;
        this.password = password;
        this.role = role;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", login, role == UserRole.ROLE_ADMIN ? "Admin" : "User");
    }
}

class AppState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    List<Order> orders = new ArrayList<>();
    List<User> users = new ArrayList<>();
    int currentUserId = -1;
    UserRole currentUserRole = UserRole.ROLE_USER;
}

public class Main { // Главный класс приложения
    private static final int MAX_ORDERS = 100;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_BRAND_LENGTH = 20;
    private static final int MAX_FULLNAME_LENGTH = 50;
    private static final int MAX_PHONE_LENGTH = 30;
    private static final int MAX_STATUS_LENGTH = 30;
    private static final int MAX_USERS = 50;
    private static final int MAX_LOGIN_LENGTH = 15;
    private static final int MAX_PASSWORD_LENGTH = 15;
    private static final String DATE_FORMAT = "dd-MM-yyyy";

    private static final String ORDERS_FILE = "orders.dat";
    private static final String USERS_FILE = "users.dat";

    private final AppState state;
    private final Scanner scanner;
    private final SimpleDateFormat dateFormat;

    public Main() {
        this.state = new AppState();
        this.scanner = new Scanner(System.in);
        this.dateFormat = new SimpleDateFormat(DATE_FORMAT);
        this.dateFormat.setLenient(false);
    }

    private void clearInputBuffer() {
        scanner.nextLine();
    }

    private void pressAnyKeyToContinue() {
        System.out.println("\nPress Enter to continue...");
        clearInputBuffer();
    }

    @SuppressWarnings("unchecked")
    private boolean loadOrders() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(ORDERS_FILE))) {
            state.orders = (List<Order>) ois.readObject();
            return true;
        } catch (IOException | ClassNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean loadUsers() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(USERS_FILE))) {
            state.users = (List<User>) ois.readObject();
            return true;
        } catch (IOException | ClassNotFoundException e) {
            return false;
        }
    }

    private boolean saveOrders() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ORDERS_FILE))) {
            oos.writeObject(state.orders);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(state.users);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void createDefaultAdmin() {
        state.users.clear();
        state.users.add(new User("admin", "admin", UserRole.ROLE_ADMIN));

        if (!saveUsers()) {
            System.out.println("Error creating users file");
            return;
        }

        System.out.println("Default admin created. Login: admin Password: admin");
        pressAnyKeyToContinue();
    }

    private void createEmptyOrdersFile() {
        state.orders.clear();

        if (!saveOrders()) {
            System.out.println("Error creating orders file");
            return;
        }

        System.out.println("Empty orders file created");
    }

    private boolean initializeData() {
        state.currentUserId = -1;
        state.currentUserRole = UserRole.ROLE_USER;

        boolean usersLoaded = loadUsers();
        boolean ordersLoaded = loadOrders();

        if (!usersLoaded) {
            createDefaultAdmin();
            usersLoaded = loadUsers();
        }

        if (!ordersLoaded) {
            createEmptyOrdersFile();
            ordersLoaded = loadOrders();
        }

        return usersLoaded && ordersLoaded;
    }

    private boolean isLoginValid(String login) {
        return login != null && !login.isEmpty() && login.length() <= MAX_LOGIN_LENGTH;
    }

    private boolean isPasswordValid(String password) {
        return password != null && !password.isEmpty() && password.length() <= MAX_PASSWORD_LENGTH;
    }

    private boolean isLoginUnique(String login) {
        return state.users.stream().noneMatch(user -> user.login.equals(login));
    }

    private boolean verifyCredentials(String login, String password) {
        for (int i = 0; i < state.users.size(); i++) {
            User user = state.users.get(i);
            if (user.login.equals(login) && user.password.equals(password)) {
                state.currentUserId = i;
                state.currentUserRole = user.role;
                return true;
            }
        }
        return false;
    }

    private String getLimitedInput(String prompt, int maxLength) {
        System.out.print(prompt + " (макс. " + maxLength + " символов): ");
        String input = scanner.nextLine();
        if (input.length() > maxLength) {
            System.out.println("Внимание: введено " + input.length() + " символов, будет обрезано до " + maxLength);
            return input.substring(0, maxLength);
        }
        return input;
    }

    private boolean isOverdue(Date appointmentDate, Date issueDate) {
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(appointmentDate);

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(issueDate);

        int daysBetween = 0;
        while (startCal.before(endCal)) {
            startCal.add(Calendar.DAY_OF_MONTH, 1);
            daysBetween++;
        }

        return daysBetween > 21;
    }


    private boolean isValidDate(String date) {
        if (date == null || date.length() != 10 || date.charAt(2) != '-' || date.charAt(5) != '-') {
            return false;
        }

        try {
            dateFormat.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private void showLoginScreen() {
        while (state.currentUserId == -1) {
            System.out.println("\n=== Система управления заказами сервисного центра ===");
            System.out.println("Пожалуйста войдите в аккаунт чтобы продолжить\n");

            System.out.print("Login: ");
            String login = scanner.next();
            System.out.print("Password: ");
            String password = scanner.next();
            scanner.nextLine();

            if (!isLoginValid(login)) {
                System.out.println("Неправильный формат логина (максимум " + MAX_LOGIN_LENGTH + " символов)");
                pressAnyKeyToContinue();
                continue;
            }

            if (!isPasswordValid(password)) {
                System.out.println("Неправильный формат пароля (максимум " + MAX_PASSWORD_LENGTH + " символов)");
                pressAnyKeyToContinue();
                continue;
            }

            if (verifyCredentials(login, password)) {
                System.out.println("\nЛогин и пароль верны! Добро пожаловать, " + login + ".");
                pressAnyKeyToContinue();
            } else {
                System.out.println("\nНеправильный логин или пароль");
                pressAnyKeyToContinue();
            }
        }
    }

    private void addUser() {
        if (state.users.size() >= MAX_USERS) {
            System.out.println("Достигнуто максимальное количество пользователей (" + MAX_USERS + ")");
            pressAnyKeyToContinue();
            return;
        }

        System.out.print("Введите логин (максимум " + MAX_LOGIN_LENGTH + " символов): ");
        String login = scanner.next();

        if (!isLoginUnique(login)) {
            System.out.println("Логин уже существует");
            pressAnyKeyToContinue();
            return;
        }

        System.out.print("Введите пароль (максимум " + MAX_PASSWORD_LENGTH + " символов): ");
        String password = scanner.next();

        System.out.print("Введите роль (1 - admin, 2 - user): ");
        int roleValue = scanner.nextInt();

        if (roleValue != 1 && roleValue != 2) {
            System.out.println("Некорректная роль");
            pressAnyKeyToContinue();
            return;
        }

        UserRole role = UserRole.fromValue(roleValue);
        state.users.add(new User(login, password, role));

        if (saveUsers()) {
            System.out.println("Пользователь успешно добавлен");
        } else {
            System.out.println("Не удалось сохранить данные пользователя");
        }
        pressAnyKeyToContinue();
    }

    private void editUser() {
        if (state.users.isEmpty()) {
            System.out.println("Пользователи не найдены");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("Список пользователей:");
        for (int i = 0; i < state.users.size(); i++) {
            System.out.println((i + 1) + ". " + state.users.get(i));
        }

        System.out.print("\nВыберите пользователя для редактирвоания (0 чтобы выйти): ");
        int choice = scanner.nextInt();

        if (choice < 1 || choice > state.users.size()) {
            if (choice != 0) {
                System.out.println("Некорректный выбор выбор");
                pressAnyKeyToContinue();
            }
            return;
        }

        int userIndex = choice - 1;
        if (userIndex == state.currentUserId) {
            System.out.println("Вы не можете редактировать свой собственный аккаунт");
            pressAnyKeyToContinue();
            return;
        }

        User user = state.users.get(userIndex);
        System.out.println("\nРедактирования пользователя: " + user.login);
        System.out.println("1. Изменение пароля");
        System.out.println("2. Изменение роли");
        System.out.println("0. Закончить редактирование");
        System.out.print("Выбор: ");
        choice = scanner.nextInt();

        switch (choice) {
            case 1:
                System.out.print("Новый пароль: ");
                user.password = scanner.next();
                System.out.println("Пароль успешно изменён");
                break;
            case 2:
                System.out.print("Новая роль (1 - admin, 2 - user): ");
                int roleValue = scanner.nextInt();
                if (roleValue == 1 || roleValue == 2) {
                    user.role = UserRole.fromValue(roleValue);
                    System.out.println("Роль успешно изменена");
                } else {
                    System.out.println("Некорректная роль");
                }
                break;
            case 0:
                return;
            default:
                System.out.println("Invalid choice");
                break;
        }

        if (choice == 1 || choice == 2) {
            saveUsers();
        }
        pressAnyKeyToContinue();
    }

    private void deleteUser() {
        if (state.users.isEmpty()) {
            System.out.println("Пользователи не найдены");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("Список пользователей:");
        for (int i = 0; i < state.users.size(); i++) {
            System.out.println((i + 1) + ". " + state.users.get(i));
        }

        System.out.print("\nВыберите пользователя для удаления  (0 чтобы выйти): ");
        int choice = scanner.nextInt();

        if (choice < 1 || choice > state.users.size()) {
            if (choice != 0) {
                System.out.println("Некорректный выбор");
            }
            pressAnyKeyToContinue();
            return;
        }

        int userIndex = choice - 1;
        if (userIndex == state.currentUserId) {
            System.out.println("Вы не можете удалить свой же аккаунт");
            pressAnyKeyToContinue();
            return;
        }

        User user = state.users.get(userIndex);
        System.out.print("Вы уверены что хотите удалить пользователя " + user.login + "? (1 - Да, 0 - Нет): ");
        choice = scanner.nextInt();

        if (choice == 1) {
            state.users.remove(userIndex);

            if (state.currentUserId > userIndex) {
                state.currentUserId--;
            }

            if (saveUsers()) {
                System.out.println("Пользователь успешно удалён");
            } else {
                System.out.println("Не получилось сохранить изменения");
            }
        }
        pressAnyKeyToContinue();
    }

    private void displayUsers() {
        if (state.users.isEmpty()) {
            System.out.println("Пользователи не найдены");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("=== Список пользователей ===\n");
        for (int i = 0; i < state.users.size(); i++) {
            System.out.println((i + 1) + ". " + state.users.get(i));
        }
        pressAnyKeyToContinue();
    }

    private void addOrder() {
        if (state.orders.size() >= MAX_ORDERS) {
            System.out.println("Достигнуто максимальное количество заказов (" + MAX_ORDERS + ")");
            pressAnyKeyToContinue();
            return;
        }

        Order newOrder = new Order();
        clearInputBuffer();

        // Использование метода с повторным запросом
        newOrder.name = getLimitedInput("Название устройства", MAX_NAME_LENGTH);
        newOrder.brand = getLimitedInput("Марка", MAX_BRAND_LENGTH);
        newOrder.fullName = getLimitedInput("ФИО заказчика", MAX_FULLNAME_LENGTH);

        System.out.print("Цена: ");
        newOrder.price = scanner.nextFloat();
        clearInputBuffer();

        newOrder.phone = getLimitedInput("Телефон пользователя", MAX_PHONE_LENGTH);
        newOrder.status = getLimitedInput("Статус заказа", MAX_STATUS_LENGTH);

        do {
            System.out.print("Дата приёма (DD-MM-YYYY): ");
            newOrder.dateAppointment = scanner.next();
            scanner.nextLine();

            if (!isValidDate(newOrder.dateAppointment)) {
                System.out.println("Некорректный формат даты! Используйте DD-MM-YYYY.");
            }
        } while (!isValidDate(newOrder.dateAppointment));

        do {
            System.out.println("\nВыберите вариант:");
            System.out.println("1. Ввести дату выдачи (DD-MM-YYYY)");
            System.out.println("2. Установить статус: 'Товар находится в работе'");
            System.out.print("Ваш выбор: ");

            String optionStr = scanner.nextLine().trim();
            int option;

            try {
                option = Integer.parseInt(optionStr);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести число 1 или 2");
                continue;
            }
            switch (option) {
                case 1:
                    System.out.print("Дата выдачи (DD-MM-YYYY): ");
                    newOrder.dateIssue = scanner.nextLine().trim();
                    if (!isValidDate(newOrder.dateIssue)) {
                        System.out.println("Некорректный формат даты! Используйте DD-MM-YYYY.");
                    }
                    break;
                case 2:
                    newOrder.dateIssue = "Товар находится в работе";
                    System.out.println(newOrder.dateIssue);
                    break;
                default:
                    System.out.println("Некорректный выбор. Попробуйте снова.");
                    break;
            }
        } while (!isValidDate(newOrder.dateIssue) &&
                !newOrder.dateIssue.equals("Товар находится в работе"));

        state.orders.add(newOrder);

        if (saveOrders()) {
            System.out.println("Заказ успешно добавлен");
        } else {
            System.out.println("Не получилось сохранить информацию о заказе");
        }
        pressAnyKeyToContinue();
    }

    private void editOrder() {
        if (state.orders.isEmpty()) {
            System.out.println("Заказов не найдено");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("Список заказов:");
        for (int i = 0; i < state.orders.size(); i++) {
            Order order = state.orders.get(i);
            System.out.println((i + 1) + ". " + order.name);
            System.out.println("   Марка: " + order.brand);
            System.out.println("   ФИО заказчика: " + order.fullName);
            System.out.println("   Цена: " + order.price);
            System.out.println("   Статус заказа: " + order.status);
            System.out.println("   Телефон заказчика: " + order.phone);
            System.out.println("   Дата приёма: " + order.dateAppointment);
            System.out.println("   Дата выдачи: " + order.dateIssue + "\n");
        }

        System.out.print("\nВыберите заказ для редактирования (0 чтобы выйти): ");
        int choice = scanner.nextInt();

        if (choice < 1 || choice > state.orders.size()) {
            if (choice != 0) {
                System.out.println("Некорректный выбор");
                pressAnyKeyToContinue();
            }
            return;
        }

        Order order = state.orders.get(choice - 1);
        int editChoice;

        do {
            System.out.println("\nРедактировать заказ: " + order.name);
            System.out.println("Марка: " + order.brand);
            System.out.println("Текущиее ФИО заказчика: " + order.fullName);
            System.out.println("Текущая цена: " + order.price);
            System.out.println("Текущий статус: " + order.status);
            System.out.println("Текущий номер телефона заказчика: " + order.phone);
            System.out.println("Дата приёма (DD-MM-YYYY): " + order.dateAppointment);
            System.out.println("Дата выдачи (DD-MM-YYYY): " + order.dateIssue + "\n");

            System.out.println("1. Редактировать название");
            System.out.println("2. Редактировать марку");
            System.out.println("3. Редактировать ФИО закзчика");
            System.out.println("4. Редактировать цену");
            System.out.println("5. Редактировать статус");
            System.out.println("6. Редактировать номер телефона заказчика");
            System.out.println("7. Редактировать дату приёма");
            System.out.println("8. Редактировать дату выдачи");
            System.out.println("0. Завершить редактирование");
            System.out.print("Выбор: ");
            editChoice = scanner.nextInt();

            clearInputBuffer();
            switch (editChoice) {
                case 1:
                    System.out.print("Новое название: ");
                    order.name = scanner.nextLine();
                    break;
                case 2:
                    System.out.print("новая марка: ");
                    order.brand = scanner.nextLine();
                    break;
                case 3:
                    System.out.print("Новое ФИО заказчика: ");
                    order.fullName = scanner.nextLine();
                    break;
                case 4:
                    System.out.print("Новая цена: ");
                    order.price = scanner.nextFloat();
                    clearInputBuffer();
                    break;
                case 5:
                    System.out.print("Новый статус: ");
                    order.status = scanner.nextLine();
                    break;
                case 6:
                    System.out.print("Новый телефон заказчика: ");
                    order.phone = scanner.nextLine();
                    break;
                case 7:
                    do {
                        System.out.print("Новая дата приёма (DD-MM-YYYY): ");
                        order.dateAppointment = scanner.next();
                        if (!isValidDate(order.dateAppointment)) {
                            System.out.println("Некорректный формат даты (use DD-MM-YYYY)");
                        }
                    } while (!isValidDate(order.dateAppointment));
                    break;
                case 8:
                    do {
                        System.out.println("\nВыберите вариант:");
                        System.out.println("1. Ввести новую дату выдачи (DD-MM-YYYY)");
                        System.out.println("2. Установить статус: 'Товар находится в работе'");
                        System.out.print("Ваш выбор: ");

                        String optionStr = scanner.nextLine().trim();
                        int option;

                        try {
                            option = Integer.parseInt(optionStr);
                        } catch (NumberFormatException e) {
                            System.out.println("Ошибка: нужно ввести число 1 или 2");
                            continue;
                        }

                        switch (option) {
                            case 1:
                                System.out.print("Новая дата выдачи (DD-MM-YYYY): ");
                                order.dateIssue = scanner.nextLine().trim();
                                if (!isValidDate(order.dateIssue)) {
                                    System.out.println("Некорректный формат даты (используйте DD-MM-YYYY)");
                                }
                                break;
                            case 2:
                                order.dateIssue = "Товар находится в работе";
                                System.out.println(order.dateIssue);
                                break;
                            default:
                                System.out.println("Некорректный выбор. Попробуйте снова.");
                                break;
                        }

                    } while (!isValidDate(order.dateIssue) &&
                            !order.dateIssue.equals("Товар находится в работе"));
                    break;
            }

            if (editChoice != 0) {
                pressAnyKeyToContinue();
            }
        } while (editChoice != 0);

        if (saveOrders()) {
            System.out.println("Изменения успешно изменены");
        } else {
            System.out.println("Не удалось сохранить изменения");
        }
        pressAnyKeyToContinue();
    }

    private void deleteOrder() {
        if (state.orders.isEmpty()) {
            System.out.println("Заказов не найдено");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("Список заказов:");
        for (int i = 0; i < state.orders.size(); i++) {
            Order order = state.orders.get(i);
            System.out.println((i + 1) + ". " + order.name + " (Марка: " + order.brand + ")");
        }

        System.out.print("\nВыберите заказ для удаления (0 чтобы выйти): ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // очистка '\n' после nextInt()

        if (choice < 1 || choice > state.orders.size()) {
            if (choice != 0) {
                System.out.println("Некорректный выбор");
            }
            pressAnyKeyToContinue();
            return;
        }

        int selectedIndex = choice - 1; // сохраняем индекс выбранного заказа
        Order order = state.orders.get(selectedIndex);

        System.out.print("Вы действительно хотите удалить заказ " + order.name + "? (1 - Да, 0 - Нет): ");
        int confirm = scanner.nextInt();
        scanner.nextLine(); // очистка '\n'

        if (confirm == 1) {
            state.orders.remove(selectedIndex);

            if (saveOrders()) {
                System.out.println("Заказ успешно удалён");
            } else {
                System.out.println("Не удалось сохранить изменения");
            }
        }
        pressAnyKeyToContinue();
    }


    private void displayOrders() {
        displayOrders(state.orders);
    }

    private void displayOrders(List<Order> list) {
        if (list.isEmpty()) {
            System.out.println("Заказов не найдено");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("=== Список заказов ===\n");
        for (int i = 0; i < list.size(); i++) {
            Order order = list.get(i);
            System.out.println((i + 1) + ". " + order.name);
            System.out.println("   Марка: " + order.brand);
            System.out.println("   ФИО заказчика: " + order.fullName);
            System.out.println("   Цена: " + order.price);
            System.out.println("   Статус заказа: " + order.status);
            System.out.println("   Телефонный номер заказчика: " + order.phone);
            System.out.println("   Дата приёма: " + order.dateAppointment);
            System.out.println("   Дата выдачи: " + order.dateIssue + "\n");
        }
        pressAnyKeyToContinue();
    }

    private void sortOrders() { // добавить сортирвоку по параметрам, а не только по имени
        if (state.orders.isEmpty()) {
            System.out.println("Нет заказов для сортирвоки");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("Сортировка по:");
        System.out.println("1. Названию");
        System.out.println("2. Модели");
        System.out.println("3. ФИО заказчика");
        System.out.println("4. Номеру заказчика");
        System.out.println("5. Статусу заказа");
        System.out.println("6. Цене");

        System.out.print("Выбор: ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                state.orders.sort(Comparator.comparing(d -> d.name));
                System.out.println("Обувь отсортирована по названию");
                break;
            case 2:
                state.orders.sort(Comparator.comparing(d -> d.brand));
                System.out.println("Обувь отсортирована по модели");
                break;
            case 3:
                state.orders.sort(Comparator.comparing(d -> d.fullName));
                System.out.println("Заказы отсортированы по ФИО заказчика");
                break;
            case 4:
                state.orders.sort(Comparator.comparingInt(d -> Integer.parseInt(d.phone)));
                System.out.println("Заказы отсортированы по Номеру заказчика");
                break;
            case 5:
                state.orders.sort(Comparator.comparing(d -> d.status));
                System.out.println("Заказы отсортированы по статусу");
                break;
            case 6:
                state.orders.sort(Comparator.comparing(d -> d.price));
                System.out.println("Заказы отсортированы по цене");
                break;

            default:
                System.out.println("Неверный выбор");
                break;
        }

        pressAnyKeyToContinue();
    }

    private void searchOrders() {
        if (state.orders.isEmpty()) {
            System.out.println("Нет заказов для поиска");
            pressAnyKeyToContinue();
            return;
        }

        while (true) {
            System.out.println("\n=== Поиск заказов ===");
            System.out.println("1. Поиск по названию");
            System.out.println("2. Поиск по марке");
            System.out.println("3. Поиск по ФИО заказчика");
            System.out.println("4. Поиск по статусу заказа");
            System.out.println("0. Выход из меню поиска");
            System.out.print("Выбор: ");

            String choiceStr = scanner.nextLine().trim();
            int choice;

            try {
                choice = Integer.parseInt(choiceStr);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести число от 0 до 4");
                continue;
            }

            if (choice == 0) {
                System.out.println("Выход из меню поиска...");
                break;
            }

            List<Order> results = new ArrayList<>();

            switch (choice) {
                case 1:
                    System.out.print("Введите название для поиска: ");
                    String nameQuery = scanner.nextLine().trim().toLowerCase();
                    if (!nameQuery.isEmpty()) {
                        for (Order s : state.orders) {
                            if (s.name != null && s.name.toLowerCase().contains(nameQuery)) {
                                results.add(s);
                            }
                        }
                    }
                    break;

                case 2:
                    System.out.print("Введите марку для поиска: ");
                    String brandQuery = scanner.nextLine().trim().toLowerCase();
                    if (!brandQuery.isEmpty()) {
                        for (Order s : state.orders) {
                            if (s.brand != null && s.brand.toLowerCase().contains(brandQuery)) {
                                results.add(s);
                            }
                        }
                    }
                    break;

                case 3:
                    System.out.print("Введите ФИО заказчика для поиска: ");
                    String fullNameQuery = scanner.nextLine().trim().toLowerCase();
                    if (!fullNameQuery.isEmpty()) {
                        for (Order s : state.orders) {
                            if (s.fullName != null && s.fullName.toLowerCase().contains(fullNameQuery)) {
                                results.add(s);
                            }
                        }
                    }
                    break;

                case 4:
                    System.out.print("Введите статус заказа для поиска: ");
                    String statusQuery = scanner.nextLine().trim().toLowerCase();
                    if (!statusQuery.isEmpty()) {
                        for (Order s : state.orders) {
                            if (s.status != null && s.status.toLowerCase().contains(statusQuery)) {
                                results.add(s);
                            }
                        }
                    }
                    break;

                default:
                    System.out.println("Неверный выбор. Введите число от 0 до 4");
                    break;
            }

            // Показываем результаты поиска
            if (results.isEmpty()) {
                System.out.println("Заказы не найдены");
            } else {
                System.out.println("\n=== РЕЗУЛЬТАТЫ ПОИСКА ===");
                System.out.println("Найдено заказов: " + results.size());
                displayOrders(results); // перегрузка для списка
            }

            pressAnyKeyToContinue();
        }
    }

    private Date parseDate(String dateStr) {
        try {
            return dateFormat.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }

    private void displayUnfilledOrders() {
        if (state.orders.isEmpty()) {
            System.out.println("Нет заказов для проверки");
            pressAnyKeyToContinue();
            return;
        }

        System.out.println("=== Просроченные заказы ===\n");
        boolean hasOverdue = false;

        for (int i = 0; i < state.orders.size(); i++) {
            Order order = state.orders.get(i);

            if ("Товар находится в работе".equalsIgnoreCase(order.dateIssue)) {
                continue;
            }

            Date appointmentDate = parseDate(order.dateAppointment);
            Date issueDate = parseDate(order.dateIssue);

            if (appointmentDate != null && issueDate != null) {
                if (isOverdue(appointmentDate, issueDate)) {
                    System.out.println((i + 1) + ". " + order.name +
                            " | Марка: " + order.brand +
                            " | Дата приёма: " + order.dateAppointment +
                            " | Дата выдачи: " + order.dateIssue +
                            " | Статус заказа: " + order.status);
                    hasOverdue = true;
                }
            }
        }

        if (!hasOverdue) {
            System.out.println("Нет просроченных заказов");
        }

        System.out.println("\n=== Заказы ожидающие выполнения ===\n");
        boolean hasPending = false;

        for (int i = 0; i < state.orders.size(); i++) {
            Order order = state.orders.get(i);

            if ("Товар находится в работе".equalsIgnoreCase(order.dateIssue)) {
                System.out.println((i + 1) + ". " + order.name +
                        " | Марка: " + order.brand +
                        " | Дата приёма: " + order.dateAppointment +
                        " | Статус заказа: " + order.status +
                        " | " + order.dateIssue);
                hasPending = true;
            }
        }

        if (!hasPending) {
            System.out.println("Нет заказов в работе");
        }

        pressAnyKeyToContinue();
    }


    private void displayTotalIncome() {
        if (state.orders.isEmpty()) {
            System.out.println("Нет заказов чтобы высчитать прибыль");
            pressAnyKeyToContinue();
            return;
        }

        System.out.print("Введите начальную дату (DD-MM-YYYY): ");
        String startDateStr = scanner.next();
        System.out.print("Введите конечную дату (DD-MM-YYYY): ");
        String endDateStr = scanner.next();

        Date startDate = parseDate(startDateStr);
        Date endDate = parseDate(endDateStr);

        if (startDate == null || endDate == null || startDate.after(endDate)) {
            System.out.println("Ошибка: Некорректный временной промежуток.");
            pressAnyKeyToContinue();
            return;
        }

        float totalIncome = 0.0f;

        for (Order order : state.orders) {
            Date issueDate = parseDate(order.dateIssue);
            if (issueDate != null && !issueDate.before(startDate) && !issueDate.after(endDate)) {
                totalIncome += order.price;
            }
        }

        System.out.println("\n=== Общая прибыль ===");
        System.out.printf("От %s до %s: %.2f\n", startDateStr, endDateStr, totalIncome);

        pressAnyKeyToContinue();
    }

    private void showUserMenu() {
        int choice;

        do {
            System.out.println("\n=== Меню пользователя ===");
            System.out.println("1. Показать список заказов");
            System.out.println("2. Сортировать заказы по параметрам");
            System.out.println("3. Показать список незавершённых заказов");
            System.out.println("4. Показать общий доход");
            System.out.println("0. Выйти из аккаунта");
            System.out.print("\nВыбор: ");

            choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1: displayOrders(); break;
                case 2: sortOrders(); break;
                case 3: displayUnfilledOrders(); break;
                case 4: displayTotalIncome(); break;
                case 0: state.currentUserId = -1; break;
                default:
                    System.out.println("Нкорректный выбор");
                    pressAnyKeyToContinue();
                    break;
            }
        } while (choice != 0);
    }

    private void showAdminMenu() {
        int choice;

        do {
            System.out.println("\n=== Меню администратора ===");
            System.out.println("1. Управление заказами");
            System.out.println("2. Управление пользователями");
            System.out.println("0. Выйти из аккаунта");
            System.out.print("\nВыбор: ");
            choice = scanner.nextInt();

            switch (choice) {
                case 1: {
                    int orderChoice;
                    do {
                        System.out.println("\n=== Управление заказами ===");
                        System.out.println("1. Добавить заказ");
                        System.out.println("2. Редактировать заказ");
                        System.out.println("3. Удалить заказ");
                        System.out.println("4. Показать список заказов");
                        System.out.println("5. Сортировать заказы по параметрам");
                        System.out.println("6. Поиск заказов по параметрам");
                        System.out.println("7. Вывести список незавершённых заказов");
                        System.out.println("8. Вывести общий доход");
                        System.out.println("0. Вернуться в главное меню");
                        System.out.print("\nChoice: ");
                        orderChoice = scanner.nextInt();

                        switch (orderChoice) {
                            case 1: addOrder(); break;
                            case 2: editOrder(); break;
                            case 3: deleteOrder(); break;
                            case 4: displayOrders(); break;
                            case 5: sortOrders(); break;
                            case 6: searchOrders(); break;
                            case 7: displayUnfilledOrders(); break;
                            case 8: displayTotalIncome(); break;
                            case 0: break;
                            default:
                                System.out.println("Некорректный выбор");
                                pressAnyKeyToContinue();
                                break;
                        }
                    } while (orderChoice != 0);
                    break;
                }

                case 2: {
                    int userChoice;
                    do {
                        System.out.println("\n=== Управление пользователями ===");
                        System.out.println("1. Добавить пользователя");
                        System.out.println("2. Редактировать пользователя");
                        System.out.println("3. Удалить пользователя");
                        System.out.println("4. Показать список пользователей");
                        System.out.println("0. Вернуться в главное меню");
                        System.out.print("\nВыбор: ");
                        userChoice = scanner.nextInt();

                        switch (userChoice) {
                            case 1: addUser(); break;
                            case 2: editUser(); break;
                            case 3: deleteUser(); break;
                            case 4: displayUsers(); break;
                            case 0: break;
                            default:
                                System.out.println("Некорректный выбор");
                                pressAnyKeyToContinue();
                                break;
                        }
                    } while (userChoice != 0);
                    break;
                }

                case 0:
                    state.currentUserId = -1;
                    break;

                default:
                    System.out.println("Некорректный выбор");
                    pressAnyKeyToContinue();
                    break;
            }
        } while (choice != 0);
    }

    public void run() {
        if (!initializeData()) {
            System.out.println("Не удалось инициализировать данные");
            return;
        }

        while (true) {
            showLoginScreen();

            if (state.currentUserId == -1) {
                break;
            }

            if (state.currentUserRole == UserRole.ROLE_ADMIN) {
                showAdminMenu();
            } else {
                showUserMenu();
            }
        }

        System.out.println("\nДосвидание!");
        scanner.close();
    }

    public static void main(String[] args) {
        new Main().run();
    }
}