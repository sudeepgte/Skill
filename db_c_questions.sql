USE SkillHubDB;

INSERT INTO exam_questions (course_id, question_text, optiona, optionb, optionc, optiond, correct_option) VALUES
(14, 'Who is the father of C language?', 'Steve Jobs', 'James Gosling', 'Dennis Ritchie', 'Rasmus Lerdorf', 'C'),
(14, 'All keywords in C are in _____', 'LowerCase letters', 'UpperCase letters', 'CamelCase letters', 'None of the mentioned', 'A'),
(14, 'What is an example of iteration in C?', 'for', 'while', 'do-while', 'All of the mentioned', 'D'),
(14, 'How is the 3rd element in an array accessed based on pointer notation?', '*a + 3', '*(a + 3)', '*(*a + 3)', '&(a + 3)', 'B'),
(14, 'Which data type is used to represent the absence of parameters?', 'int', 'short', 'void', 'float', 'C'),
(14, 'What is the standard library for Input/Output functions in C?', 'stdlib.h', 'string.h', 'math.h', 'stdio.h', 'D'),
(14, 'Which operator is used to find the address of a variable?', '*', '&', '%', '#', 'B'),
(14, 'What does `sizeof` in C return?', 'The total size of an array in bits', 'The size of a variable in bytes', 'The memory address of a variable', 'The value stored in a variable', 'B'),
(14, 'Which type of loops evaluates the condition after executing the body?', 'for loop', 'while loop', 'do-while loop', 'Foreach loop', 'C'),
(14, 'What is the extension of a C source code file?', '.cpp', '.c', '.cs', '.csharp', 'B'),
(14, 'Which function is used to dynamically allocate memory in C?', 'alloc()', 'memalloc()', 'malloc()', 'create()', 'C'),
(14, 'In C, what character indicates the end of a string?', '\\n', '\\0', '\\t', 'EOF', 'B'),
(14, 'A C program execution begins from which function?', 'start()', 'main()', 'init()', 'system()', 'B'),
(14, 'What is a pointer in C?', 'A keyword used to create variables', 'A variable that stores address of an instruction', 'A variable that stores address of other variable', 'All of the above', 'C'),
(14, 'Which function is used to read a string from standard input?', 'gets()', 'get()', 'read()', 'scns()', 'A'),
(14, 'Which operator has the highest precedence?', '&&', '||', '==', '()', 'D'),
(14, 'What will `7 / 2` evaluate to in C?', '3.5', '4', '3', 'Error', 'C'),
(14, 'What is the default return type if it is not specified in a function definition?', 'void', 'int', 'float', 'short', 'B'),
(14, 'How do you declare a constant variable in C?', 'const int x = 10;', 'constant int x = 10;', '#define int x = 10;', 'let x = 10;', 'A'),
(14, 'Which header file handles string operations like `strlen` and `strcpy`?', 'stdlib.h', 'string.h', 'math.h', 'ctype.h', 'B');
