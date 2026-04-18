import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 符合规则的10位密码：包含字母、特殊符号、数字
        String password = "Abc@12345";  // 10位：A-b-c-@-1-2-3-4-5
        
        String encoded = encoder.encode(password);
        System.out.println("原始密码: " + password);
        System.out.println("BCrypt加密: " + encoded);
        System.out.println("密码长度: " + password.length());
        
        // 验证
        boolean matches = encoder.matches(password, encoded);
        System.out.println("验证结果: " + matches);
    }
}
