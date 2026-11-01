## 1. 什么是 ORM？

问题：请解释 ORM 是什么，并说明下面几组概念是如何对应的：

```text
Java class  ↔ database table
Java object ↔ database row
Java field  ↔ database column
```

答案： ORM（Object-Relational Mapping，对象关系映射）是一种将面向对象程序中的对象与关系型数据库中的数据关联起来的技术。Java 类通常映射数据库表；该类的一个对象对应表中的一行；对象中的字段对应表的列。ORM 框架帮助完成 SQL 执行、结果集转换和对象状态管理，减少手写重复 JDBC 代码。

例如 `Student` 类对应 `students` 表，`new Student(1L, "Alice")` 对应一条学生记录，`name` 字段对应 `name` 列。实际映射可以通过注解自定义，并不要求类名、字段名与数据库完全一致。

## 2. JPA 和 Hibernate 有什么区别？

问题： 请分别解释 JPA 和 Hibernate 的作用，并说明为什么可以说：

```text
JPA 是标准，Hibernate 是 implementation。
```

答案：JPA（Jakarta Persistence API，旧称 Java Persistence API）是一套 Java 持久化规范，定义了 `@Entity`、`EntityManager`、查询及实体生命周期等 API 和行为要求。Hibernate 是实现 JPA 规范的 ORM 框架，负责实体映射、SQL 生成、缓存及与数据库交互等具体工作。因此 JPA 是标准（接口和规则），Hibernate 是一种实现（implementation）。项目可以面向 JPA 编程，必要时更换为其他兼容实现。

## 3. Spring Data JPA 解决了什么问题？

问题：为什么我们只需要声明下面的 interface，就可以使用 `save()` 和 `findById()`？

```java
public interface StudentRepository
        extends JpaRepository<Student, Long> {
}
```

请说明 Spring Data JPA、JPA 和 Hibernate 之间的关系。

答案： Spring Data JPA 简化了数据访问层开发。`JpaRepository<Student, Long>` 已经声明了常用 CRUD 方法，其中 `Student` 是实体类型，`Long` 是主键类型。Spring Data 在运行时为该接口创建 Repository 代理/实现，因此无需自己编写 `save()`、`findById()` 的实现。它还支持根据方法名派生查询、分页和排序。

三者关系是：Spring Data JPA → JPA API → Hibernate（常见 JPA Provider）→ JDBC → Database。Spring Data JPA 提供 Repository 抽象，JPA 定义持久化规范，Hibernate 执行具体 ORM 工作。

## 4. 什么是 Entity？

问题： 请解释 Entity 是什么，并说明下面几个 annotation 分别有什么作用：

```java
@Entity
@Table(name = "students")
@Id
@GeneratedValue
```

答案： Entity 是被 JPA 管理、可持久化到数据库的 Java 对象，通常对应一张表。

- `@Entity`：标记该 Java 类为 JPA 实体。
- `@Table(name = "students")`：指定映射到数据库中的 `students` 表；不写时使用默认表名映射规则。
- `@Id`：指定实体的主键字段，用来唯一标识实体。
- `@GeneratedValue`：配置主键值的生成方式，例如数据库 identity 列或 sequence。

```java
import jakarta.persistence.*;

@Entity
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected Student() {} // JPA 需要无参构造器
}
```

## 5. 为什么 Entity 需要 primary key？

问题： 请解释 `@Id` 的作用，并说明为什么新创建的 Entity 在保存到 database 之前，ID 可能还没有值。

答案: 主键用于唯一识别一条数据库记录，JPA 依靠实体类型和主键来跟踪实体身份、更新和删除记录，并维护持久化上下文。`@Id` 声明哪个属性是主键。若使用 `@GeneratedValue`，ID 由持久化框架或数据库生成，所以新创建的 `Student` 对象在持久化前通常 `id == null`。何时获得 ID 取决于生成策略，例如 `IDENTITY` 通常需要执行 INSERT 才能获取数据库生成的 ID；某些 sequence 策略可以在 INSERT 前取得 ID。

## 6. 为什么 Student 和 Course 之间需要 Enrollment？

问题： 一个 Student 可以选择多门 Course，一门 Course 也可以被多个 Student 选择。请解释为什么不一定直接使用简单的 `@ManyToMany`，而是创建一个 `Enrollment` entity。请举出至少一个应该放在 Enrollment 中、而不是 Student 或 Course 中的字段。

答案：Student 和 Course 是多对多关系，但选课这件事本身也可能有业务属性，例如 `enrolledAt`（选课时间）、`grade`（成绩）、`status`（选课状态）。这些属性属于某个学生选择某门课的关系，不属于学生或课程本身。因此用 `Enrollment` 实体表示关联记录更合理，也更容易扩展、查询和管理业务规则。

```java
@Entity
public class Enrollment {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    private java.time.LocalDateTime enrolledAt;
    private String grade;
}
```

实际应用中还可以为 `(student_id, course_id)` 设置唯一约束，防止重复选课（若业务不允许重复）。

## 7. 什么是 owning side？

问题： 在下面的 relationship 中：

```java
public class Enrollment {
    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;
}
```

请说明：1. 哪一边是 owning side？2. 哪一个 database column 是 foreign key？3. owning side 负责什么？

答案：

1. Owning side 是 `Enrollment.student` 这一侧，即 `@ManyToOne` 侧。
2. Foreign key 是 `enrollment` 表中的 `student_id` 列，它引用 `students` 表的主键。
3. Owning side 决定关系在数据库中如何写入或更新。例如 `enrollment.setStudent(student)` 会改变关联的主控状态；JPA 根据这一侧维护外键。双向关系中的另一侧通常用 `mappedBy` 标记为 inverse side。为了让内存中的双向对象关系一致，业务代码通常也应同步更新两侧。

## 8. `mappedBy = "student"` 表示什么？

问题： 请解释下面的配置：

```java
@OneToMany(mappedBy = "student")
private List<Enrollment> enrollments;
```

特别说明 `"student"` 为什么是 Enrollment 中的 Java field name，而不是 database column name `student_id`。

答案： `mappedBy = "student"` 表示当前 `@OneToMany` 集合是关系的非 owning side，关联关系由 `Enrollment` 实体的 `student` 属性管理。

`mappedBy` 使用的是 Java 实体属性名，而非数据库列名。JPA 通过 `Enrollment.student` 的映射元数据知道外键列是 `student_id`，因此这里不能写 `mappedBy = "student_id"`。

```java
// Student.java
@OneToMany(mappedBy = "student")
private List<Enrollment> enrollments;

// Enrollment.java
@ManyToOne
@JoinColumn(name = "student_id")
private Student student;
```

## 9. LAZY 和 EAGER 有什么区别？

问题： 请用通俗的语言解释两者的加载时机，并说明为什么 relationship 通常不应该全部设置成 EAGER。

答案：

- LAZY（延迟加载）：先获取当前实体，关联数据在真正需要访问时再加载（也可能受 ORM 优化或查询方式影响）。
- EAGER（立即加载）：获取实体时也要求加载关联数据，但不保证一定通过同一条 SQL 完成。

如果所有关系都使用 EAGER，可能查询大量根本不需要的数据，增加数据库负载、内存占用，并引发 N+1 查询或复杂联表查询。通常根据实际使用场景选择 LAZY，并在需要时使用 `JOIN FETCH`、EntityGraph 或 DTO 查询显式获取关联数据。注意：在事务/持久化上下文关闭后访问未初始化的 LAZY 关系，可能出现 `LazyInitializationException`。

## 10. 一次请求是如何到达 database 的？

问题： 请按照下面的顺序解释每一层的职责：

```text
HTTP Request
  → Controller
  → Service
  → Repository
  → Hibernate
  → JDBC
  → Database
```

以“根据 `studentId` 查询学生的 Enrollment”为例，说明每一层大概做什么。

答案：

1. HTTP Request：客户端发出请求，例如 `GET /students/42/enrollments`。
2. Controller：接收请求，解析路径参数 `studentId = 42`，调用 Service，并把结果转成 HTTP 响应。
3. Service：执行业务逻辑，如验证学生是否存在、检查权限、调用 Repository；通常也是事务边界所在层。
4. Repository：提供数据访问接口，例如 `findByStudentId(42L)`；Spring Data JPA 将调用转交给 JPA。
5. Hibernate：依据实体映射和查询生成 SQL、绑定参数，并将查询结果映射为 `Enrollment` 对象。
6. JDBC：通过 JDBC 驱动和数据库连接执行 SQL，读取结果集。
7. Database：执行查询，返回符合条件的选课记录。

结果沿相反方向返回：Database → JDBC → Hibernate → Repository → Service → Controller → HTTP Response。

例如：

```java
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
}

@Service
public class EnrollmentService {
    private final EnrollmentRepository repository;

    public EnrollmentService(EnrollmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getEnrollments(Long studentId) {
        return repository.findByStudentId(studentId);
    }
}
```

`findByStudentId` 是 Spring Data JPA 的派生查询方法，沿 `Enrollment.student.id` 属性路径筛选记录。实际 REST API 通常应返回 DTO，避免直接序列化实体导致循环引用或懒加载问题。

---