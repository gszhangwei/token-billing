# Test Scenarios for `[Feature Name]`

## 1. `[Controller Name]` Test Scenarios
### Create `[ControllerTestClassName]` class
1. Create `[ControllerTestClassName]` class
2. Use @WebMvcTest annotation to test the `[ControllerClassName]` class
3. Use @MockBean annotation to mock the Service class
4. Use @Autowired annotation to inject the MockMvc instance
5. Create test scenarios for `[ControllerClassName]` based on the prompts below
6. Generate test code for each test scenario

#### `[testMethodName]`
- Description: `[Test description]`
- Input: `[Input description]`
- Expected Output: `[Expected output description]`
- Verification Points:
    - `[Verification point 1]`
    - `[Verification point 2]`
    - ...

## 2. `[Service Name]` Test Scenarios
### Create `[ServiceTestClassName]` class
1. Create `[ServiceTestClassName]` class
2. Use @Mock annotation to mock the Repository class
3. Use @InjectMocks annotation to inject the `[ServiceClassName]` instance
4. Create test scenarios for `[ServiceClassName]` based on the prompts below
5. Generate test code for each test scenario

#### `[testMethodName]`
- Description: `[Test description]`
- Input: `[Input description]`
- Expected Output: `[Expected output description]`
- Verification Points:
    - `[Verification point 1]`
    - `[Verification point 2]`
    - ...

## 3. `[Repository Name]` Test Scenarios
### Create `[RepositoryTestClassName]` class
1. Create `[RepositoryTestClassName]` class
2. Use @Mock annotation to mock the EntityManager
3. Use @InjectMocks annotation to inject the `[RepositoryClassName]` instance
4. Create test scenarios for `[RepositoryClassName]` based on the prompts below
5. Generate test code for each test scenario

#### `[testMethodName]`
- Description: `[Test description]`
- Input: `[Input description]`
- Expected Output: `[Expected output description]`
- Verification Points:
    - `[Verification point 1]`
    - `[Verification point 2]`
    - ...

## 4. `[DAO Name]` Test Scenarios
### Create `[DAOTestClassName]` class
1. Create `[DAOTestClassName]` class
2. Use @Mock annotation to mock the EntityManager
3. Use @InjectMocks annotation to inject the `[DAOClassName]` instance
4. Create test scenarios for `[DAOClassName]` based on the prompts below
5. Generate test code for each test scenario

#### `[testMethodName]`
- Description: `[Test description]`
- Input: `[Input description]`
- Expected Output: `[Expected output description]`
- Verification Points:
    - `[Verification point 1]`
    - `[Verification point 2]`
    - ...

## 5. Model Class Test Scenarios
### Create `[ModelTestClassName]` class
1. Create `[ModelTestClassName]` class
2. Create test scenarios for `[ModelClassName]` based on the prompts below
3. Generate test code for each test scenario

#### `[testMethodName]`
- Description: `[Test description]`
- Input: `[Input description]`
- Expected Output: `[Expected output description]`
- Verification Points:
    - `[Verification point 1]`
    - `[Verification point 2]`
    - ...

## 6. Integration Test Scenarios
### Create `[ControllerIntegrationTestClassName]` class
1. Create `[ControllerIntegrationTestClassName]` class
2. Use @SpringBootTest annotation to test the `[ControllerClassName]` class
3. Use @AutoConfigureMockMvc annotation to mock the `[ControllerClassName]` class
4. Create test scenarios for `[ControllerClassName]` based on the prompts below
5. Generate test code for each test scenario

#### `[testMethodName]`
- Description: `[Test description]`
- Input: `[Input description]`
- Expected Output: `[Expected output description]`
- Verification Points:
    - `[Verification point 1]`
    - `[Verification point 2]`
    - ...

## 7. Constraints
- Test name should follow the format: `should_return_[your expected output]_when_[your action]_given_[your input]`