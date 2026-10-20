import ui.GuestMenu;
import utils.ConsoleHelper;

public class Main {
    public static void main(String[] args) {
        ConsoleHelper.setup();
        System.out.println("==================================================");
        System.out.println("  Система автоматизации бюро ритуальных услуг  ");
        System.out.println("==================================================");
        
        // Запуск главного меню (для гостя)
        GuestMenu guestMenu = new GuestMenu();
        guestMenu.showMenu();
    }
}