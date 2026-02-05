package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserPasskey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPasskeyRepository extends JpaRepository<UserPasskey, Long> {
    /**
     * 根据 credential ID 查找 Passkey
     */
    Optional<UserPasskey> findByCredentialId(byte[] credentialId);

    /**
     * 查找用户的所有 Passkey
     */
    List<UserPasskey> findByUserId(Long userId);

    /**
     * 删除用户的所有 Passkey
     */
    void deleteByUserId(Long userId);
}
