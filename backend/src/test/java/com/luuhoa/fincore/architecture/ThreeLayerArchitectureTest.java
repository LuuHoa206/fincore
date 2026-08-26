package com.luuhoa.fincore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import com.luuhoa.fincore.identity.AuthController;
import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.CurrentUserController;
import com.luuhoa.fincore.transaction.TransactionController;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.wallet.WalletController;
import com.luuhoa.fincore.wallet.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

class ThreeLayerArchitectureTest {

    @Test
    void businessControllersDependOnServicesInsteadOfRepositories() {
        assertControllerUsesService(AuthController.class, AuthService.class);
        assertControllerUsesService(CurrentUserController.class, AuthService.class);
        assertControllerUsesService(WalletController.class, WalletService.class);
        assertControllerUsesService(TransactionController.class, TransactionService.class);
    }

    private void assertControllerUsesService(Class<?> controllerType, Class<?> serviceType) {
        assertThat(controllerType.isAnnotationPresent(RestController.class)).isTrue();
        assertThat(serviceType.isAnnotationPresent(Service.class)).isTrue();

        List<Class<?>> constructorDependencies = List.of(controllerType.getDeclaredConstructors()).stream()
                .flatMap(constructor -> List.of(constructor.getParameterTypes()).stream())
                .toList();
        assertThat(constructorDependencies).contains(serviceType);
        assertThat(constructorDependencies)
                .noneMatch(this::isRepository);

        assertThat(List.of(controllerType.getDeclaredFields()).stream().map(Field::getType))
                .noneMatch(this::isRepository);
    }

    private boolean isRepository(Class<?> dependency) {
        return JpaRepository.class.isAssignableFrom(dependency)
                || dependency.getSimpleName().endsWith("Repository");
    }
}
